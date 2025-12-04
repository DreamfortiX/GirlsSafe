from flask import Flask, request, jsonify
from flask_cors import CORS
import os
import tempfile
import sys
from werkzeug.utils import secure_filename
import traceback
import logging
import subprocess
import struct

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Configuration
ALLOWED_EXTENSIONS = {'wav', 'mp3', 'ogg', 'flac', 'm4a', '3gpp', '3gp', 'amr', 'aac', 'm4a', 'mp4'}
UPLOAD_FOLDER = tempfile.gettempdir()
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max file size

# Try to import audio processor
try:
    from audio_processor import AudioProcessor
    AUDIO_PROCESSOR_AVAILABLE = True
    logger.info("✅ Audio processor imported successfully")
except ImportError as e:
    logger.error(f"❌ Failed to import audio processor: {e}")
    AUDIO_PROCESSOR_AVAILABLE = False
    AudioProcessor = None

# Initialize model paths
MODEL_DIR = os.path.join(os.path.dirname(__file__), 'modals')
os.makedirs(MODEL_DIR, exist_ok=True)

# Look for model files
MODEL_PATH = os.path.join(MODEL_DIR, 'audio_danger_detection_cnn.h5')
SCALER_PATH = os.path.join(MODEL_DIR, 'feature_scaler.pkl')

# Check if files exist
if not os.path.exists(MODEL_PATH):
    logger.error(f"❌ Model file not found: {MODEL_PATH}")
    MODEL_PATH = None

if not os.path.exists(SCALER_PATH):
    logger.warning(f"⚠️  Scaler file not found: {SCALER_PATH}")
    SCALER_PATH = None

# Initialize audio processor
processor = None
if AUDIO_PROCESSOR_AVAILABLE and MODEL_PATH and os.path.exists(MODEL_PATH):
    try:
        processor = AudioProcessor(MODEL_PATH, SCALER_PATH)
        logger.info(f"✅ Audio Processor initialized successfully")
    except Exception as e:
        logger.error(f"❌ Failed to initialize audio processor: {e}")
        processor = None

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def detect_audio_format(file_path):
    """Detect audio format by reading file header."""
    try:
        with open(file_path, 'rb') as f:
            header = f.read(100)
        
        # Check common signatures
        if header[:4] == b'RIFF' and header[8:12] == b'WAVE':
            return 'wav'
        elif header[:3] == b'ID3':
            return 'mp3'
        elif header[:4] == b'OggS':
            return 'ogg'
        elif header[:4] == b'fLaC':
            return 'flac'
        elif b'ftyp3gp' in header[:20]:
            return '3gp'
        elif b'ftypmp4' in header[:20] or b'ftypM4A' in header[:20]:
            return 'mp4'
        elif header[:6] == b'#!AMR\n':
            return 'amr'
        elif header[:11] == b'#!AMR-WB\n':
            return 'amr-wb'
        else:
            return 'unknown'
    except:
        return 'unknown'

def create_wav_header(data_length, sample_rate=44100, channels=1, bits_per_sample=16):
    """Create a proper WAV header."""
    byte_rate = sample_rate * channels * bits_per_sample // 8
    block_align = channels * bits_per_sample // 8
    
    # WAV header structure
    header = b'RIFF'
    header += struct.pack('<I', 36 + data_length)  # Chunk size
    header += b'WAVE'
    header += b'fmt '
    header += struct.pack('<I', 16)  # Subchunk1 size
    header += struct.pack('<H', 1)   # Audio format (1 = PCM)
    header += struct.pack('<H', channels)
    header += struct.pack('<I', sample_rate)
    header += struct.pack('<I', byte_rate)
    header += struct.pack('<H', block_align)
    header += struct.pack('<H', bits_per_sample)
    header += b'data'
    header += struct.pack('<I', data_length)
    
    return header

def convert_to_wav(input_path, output_path):
    """
    Convert any audio file to standard WAV format.
    Uses multiple methods for robustness.
    """
    try:
        # Method 1: Try ffmpeg (most reliable)
        try:
            # Try to find ffmpeg
            ffmpeg_path = None
            possible_paths = [
                'ffmpeg',
                'ffmpeg.exe',
                os.path.join(os.path.dirname(__file__), 'ffmpeg', 'bin', 'ffmpeg.exe'),
                os.path.join(os.path.dirname(__file__), 'ffmpeg.exe')
            ]
            
            for path in possible_paths:
                try:
                    result = subprocess.run([path, '-version'], 
                                          capture_output=True, 
                                          text=True,
                                          timeout=2)
                    if result.returncode == 0:
                        ffmpeg_path = path
                        break
                except:
                    continue
            
            if ffmpeg_path:
                cmd = [
                    ffmpeg_path,
                    '-i', input_path,
                    '-f', 'wav',
                    '-ar', '22050',
                    '-ac', '1',
                    '-acodec', 'pcm_s16le',
                    '-y',
                    output_path
                ]
                
                result = subprocess.run(cmd, 
                                      capture_output=True, 
                                      text=True,
                                      timeout=10)
                
                if result.returncode == 0 and os.path.exists(output_path):
                    logger.info("✅ FFmpeg conversion successful")
                    return True
        except Exception as e:
            logger.warning(f"⚠️  FFmpeg conversion failed: {e}")
        
        # Method 2: Try pydub
        try:
            from pydub import AudioSegment
            
            audio = AudioSegment.from_file(input_path)
            audio = audio.set_channels(1).set_frame_rate(22050)
            audio.export(output_path, format="wav")
            
            if os.path.exists(output_path):
                logger.info("✅ Pydub conversion successful")
                return True
        except Exception as e:
            logger.warning(f"⚠️  Pydub conversion failed: {e}")
        
        # Method 3: Raw file repair (for corrupted Android WAV files)
        try:
            with open(input_path, 'rb') as f:
                raw_data = f.read()
            
            file_size = len(raw_data)
            
            # Check if it might be raw PCM data
            # Common Android PCM: 16-bit, mono, 44.1kHz or 8kHz
            
            # Try common sample rates
            sample_rates_to_try = [44100, 22050, 16000, 8000]
            
            for sample_rate in sample_rates_to_try:
                # Calculate expected size for 4 seconds of audio
                expected_size = sample_rate * 2 * 4  # 4 seconds, 16-bit = 2 bytes per sample
                
                if file_size >= expected_size:
                    # Take first 4 seconds
                    audio_data = raw_data[:expected_size]
                    
                    # Create WAV header
                    header = create_wav_header(len(audio_data), sample_rate, 1, 16)
                    
                    with open(output_path, 'wb') as f:
                        f.write(header)
                        f.write(audio_data)
                    
                    if os.path.exists(output_path):
                        logger.info(f"✅ Raw PCM conversion with {sample_rate}Hz successful")
                        return True
            
            # If none worked, just wrap in WAV header as last resort
            header = create_wav_header(file_size, 44100, 1, 16)
            with open(output_path, 'wb') as f:
                f.write(header)
                f.write(raw_data)
            
            logger.info("✅ Raw data wrapped in WAV header")
            return True
            
        except Exception as e:
            logger.error(f"❌ Raw conversion failed: {e}")
            return False
            
    except Exception as e:
        logger.error(f"❌ Conversion failed: {e}")
        return False

@app.route('/')
def home():
    """Home endpoint"""
    return jsonify({
        'status': 'online',
        'service': 'Audio Danger Detection API',
        'version': '2.1.0',
        'feature': 'Forced WAV conversion for Android audio',
        'processor_loaded': processor is not None,
        'supported_formats': list(ALLOWED_EXTENSIONS)
    })

@app.route('/upload', methods=['POST'])
def upload_audio():
    """
    Upload and analyze audio file.
    ALWAYS converts to WAV first.
    """
    if processor is None:
        return jsonify({
            'status': 'error',
            'message': 'Audio processor not available.'
        }), 500
    
    if 'file' not in request.files:
        return jsonify({
            'status': 'error',
            'message': 'No file provided.'
        }), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({
            'status': 'error',
            'message': 'No file selected'
        }), 400
    
    filename = secure_filename(file.filename)
    temp_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    converted_path = None
    
    logger.info(f"📥 Receiving file: {filename}")
    
    try:
        # Save uploaded file
        file.save(temp_path)
        original_size = os.path.getsize(temp_path)
        logger.info(f"✅ File saved: {original_size} bytes")
        
        if original_size == 0:
            return jsonify({
                'status': 'error',
                'message': 'Empty file'
            }), 400
        
        # ALWAYS convert to WAV
        converted_path = os.path.join(
            os.path.dirname(temp_path),
            f"converted_{os.path.splitext(filename)[0]}.wav"
        )
        
        logger.info(f"🔄 Converting to WAV...")
        
        if not convert_to_wav(temp_path, converted_path):
            return jsonify({
                'status': 'error',
                'message': 'Failed to convert audio to WAV format'
            }), 400
        
        converted_size = os.path.getsize(converted_path)
        logger.info(f"✅ Conversion successful: {converted_size} bytes")
        
        # Analyze the audio
        logger.info("🤖 Analyzing audio...")
        result = processor.predict_danger(converted_path)
        
        if result is None or result.get('status') == 'error':
            return jsonify({
                'status': 'error',
                'message': result.get('message', 'Analysis failed') if result else 'Unknown error'
            }), 500
        
        # Prepare response
        response = {
            'status': 'success',
            'filename': filename,
            'converted': True,
            'analysis': {
                'prediction': result.get('prediction', -1),
                'is_danger': result.get('is_danger', 0),
                'confidence': float(result.get('confidence', 0.0)),
                'class_label': result.get('class_label', 'UNKNOWN'),
                'danger_probability': float(result.get('danger_probability', 0.0)),
                'safe_probability': float(result.get('safe_probability', 0.0))
            }
        }
        
        logger.info(f"✅ Analysis complete: {response['analysis']['class_label']}")
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"❌ Error: {str(e)}")
        return jsonify({
            'status': 'error',
            'message': f'Processing error: {str(e)}'
        }), 500
        
    finally:
        # Cleanup
        try:
            if os.path.exists(temp_path):
                os.remove(temp_path)
            if converted_path and os.path.exists(converted_path):
                os.remove(converted_path)
        except:
            pass

@app.route('/test', methods=['POST'])
def test_endpoint():
    """Simple test endpoint"""
    return jsonify({
        'status': 'success',
        'message': 'Server is working',
        'processor': 'ready' if processor else 'not ready'
    })

if __name__ == '__main__':
    print("\n" + "="*60)
    print("🚀 AUDIO DANGER DETECTION SERVER")
    print("="*60)
    print(f"📁 Model: {'✅ Loaded' if processor else '❌ Not loaded'}")
    print(f"🌐 Endpoint: POST /upload")
    print(f"📡 Starting on http://0.0.0.0:5000")
    print("="*60)
    
    app.run(
        host="0.0.0.0",
        port=5000,
        debug=True,
        threaded=True
    )