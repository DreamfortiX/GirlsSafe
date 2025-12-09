import os
import numpy as np
import librosa
import tensorflow as tf
from tensorflow.keras.models import load_model
import joblib
import soundfile as sf
import warnings
import wave
import io
warnings.filterwarnings('ignore')

class AudioProcessor:
    def __init__(self, model_path, scaler_path=None):
        """Initialize the audio processor with the trained model."""
        print("🔊 Initializing Audio Processor...")
        
        # Check if model exists
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")
        
        # Determine model type
        self.model_type = 'rf' if model_path.endswith('.pkl') else 'cnn'
        print(f"📊 Detected model type: {self.model_type.upper()}")
        
        # Load model
        if self.model_type == 'cnn':
            self.model = load_model(model_path, compile=False)
            self.model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])
        else:  # RF
            self.model = joblib.load(model_path)
        
        # Load scaler if provided
        if scaler_path and os.path.exists(scaler_path):
            self.scaler = joblib.load(scaler_path)
            print(f"✅ Loaded scaler from: {scaler_path}")
        else:
            self.scaler = None
            print("⚠️  No scaler loaded")
        
        # Audio parameters (MUST MATCH GOOGLE COLAB EXACTLY)
        self.target_sr = 22050  # Sampling rate
        self.duration = 4.0     # Duration in seconds
        self.n_mfcc = 13        # Number of MFCC coefficients
        
        print("✅ Audio Processor initialized successfully!")
        print(f"   Target SR: {self.target_sr} Hz")
        print(f"   Duration: {self.duration} seconds")
        print(f"   MFCC Coefficients: {self.n_mfcc}")

    def extract_features(self, audio_path):
        """
        Extract MFCC features from audio file with robust audio loading
        """
        try:
            print(f"📊 Loading audio: {os.path.basename(audio_path)}")
            
            # Verify file exists
            if not os.path.exists(audio_path):
                raise FileNotFoundError(f"Audio file not found: {audio_path}")
            
            file_size = os.path.getsize(audio_path)
            print(f"📁 File size: {file_size} bytes")
            
            # METHOD 1: Try librosa with error handling
            audio = None
            sr = None
            max_retries = 3
            
            for attempt in range(max_retries):
                try:
                    # Try with librosa first (most flexible)
                    audio, sr = librosa.load(
                        audio_path, 
                        sr=self.target_sr, 
                        duration=self.duration,
                        mono=True,
                        offset=0.0
                    )
                    print(f"✅ Successfully loaded with librosa (attempt {attempt + 1})")
                    print(f"   Samples: {len(audio)}, SR: {sr} Hz")
                    break
                except Exception as e:
                    if attempt == max_retries - 1:
                        print(f"⚠️  Librosa failed: {e}")
                    else:
                        print(f"⚠️  Librosa attempt {attempt + 1} failed, retrying...")
                        continue
            
            # METHOD 2: If librosa fails, try raw file reading
            if audio is None:
                print("🔄 Trying alternative loading methods...")
                try:
                    # Read raw bytes and try different methods
                    with open(audio_path, 'rb') as f:
                        raw_data = f.read()
                    
                    # Check if it's a WAV file
                    if raw_data[:4] == b'RIFF':
                        print("📄 Detected RIFF WAV format")
                        try:
                            # Try soundfile
                            audio, sr = sf.read(io.BytesIO(raw_data))
                            if len(audio.shape) > 1:
                                audio = np.mean(audio, axis=1)  # Convert to mono
                        except:
                            # Try wave module
                            with wave.open(io.BytesIO(raw_data)) as wav_file:
                                sr = wav_file.getframerate()
                                n_frames = wav_file.getnframes()
                                audio_data = wav_file.readframes(n_frames)
                                audio = np.frombuffer(audio_data, dtype=np.int16)
                                audio = audio.astype(np.float32) / 32768.0
                    
                    print(f"✅ Loaded with alternative method")
                    print(f"   Samples: {len(audio)}, SR: {sr} Hz")
                    
                except Exception as alt_error:
                    print(f"❌ All loading methods failed: {alt_error}")
                    return None, False
            
            # Resample if needed
            if sr != self.target_sr:
                print(f"🔄 Resampling from {sr} Hz to {self.target_sr} Hz")
                audio = librosa.resample(audio, orig_sr=sr, target_sr=self.target_sr)
                sr = self.target_sr
            
            # Ensure exact length for the specified duration
            target_length = int(self.target_sr * self.duration)
            if len(audio) < target_length:
                padding = target_length - len(audio)
                print(f"📏 Padding with {padding} zeros")
                audio = np.pad(audio, (0, padding), mode='constant')
            elif len(audio) > target_length:
                print(f"✂️  Trimming to {target_length} samples")
                audio = audio[:target_length]
            
            print(f"📊 Final audio: {len(audio)} samples ({len(audio)/self.target_sr:.2f} seconds)")
            
            # Extract MFCC features
            print("🎵 Extracting MFCC features...")
            mfccs = librosa.feature.mfcc(
                y=audio, 
                sr=sr, 
                n_mfcc=self.n_mfcc,
                n_fft=2048,
                hop_length=512
            )
            
            print(f"📊 MFCC shape: {mfccs.shape}")
            
            # Aggregate features
            mfccs_mean = np.mean(mfccs, axis=1)
            mfccs_std = np.std(mfccs, axis=1)
            
            # Handle NaN/Inf values
            mfccs_mean = np.nan_to_num(mfccs_mean, nan=0.0, posinf=0.0, neginf=0.0)
            mfccs_std = np.nan_to_num(mfccs_std, nan=0.0, posinf=0.0, neginf=0.0)
            
            # Combine features
            features = np.hstack([mfccs_mean, mfccs_std])
            
            print(f"✅ Features extracted: shape={features.shape}")
            print(f"   Mean range: [{np.min(mfccs_mean):.4f}, {np.max(mfccs_mean):.4f}]")
            print(f"   Std range: [{np.min(mfccs_std):.4f}, {np.max(mfccs_std):.4f}]")
            
            return features, True
            
        except Exception as e:
            print(f"❌ Error in extract_features: {str(e)}")
            import traceback
            traceback.print_exc()
            return None, False

    def preprocess_features(self, features):
        """Prepare features for model prediction."""
        if self.model_type == 'cnn':
            # Reshape for CNN: (1, 13, 2, 1)
            features_2d = features.reshape(13, 2)
            features_reshaped = features_2d.reshape(1, 13, 2, 1)
            print(f"📊 CNN input shape: {features_reshaped.shape}")
            return features_reshaped
        else:  # RF
            features_reshaped = features.reshape(1, -1)
            if self.scaler:
                features_reshaped = self.scaler.transform(features_reshaped)
            print(f"📊 RF input shape: {features_reshaped.shape}")
            return features_reshaped

    def predict_danger(self, audio_path):
        """Make prediction on audio file."""
        print(f"\n🎯 Analyzing audio: {os.path.basename(audio_path)}")
        
        # Extract features
        features, success = self.extract_features(audio_path)
        if not success or features is None:
            return {
                'status': 'error',
                'message': 'Failed to extract features from audio file'
            }
        
        # Preprocess features
        model_input = self.preprocess_features(features)
        
        try:
            # Make prediction
            if self.model_type == 'cnn':
                prediction_proba = self.model.predict(model_input, verbose=0)
            else:  # RF
                prediction_proba = self.model.predict_proba(model_input)
            
            # Get prediction results
            prediction = np.argmax(prediction_proba, axis=1)[0]
            confidence = float(np.max(prediction_proba))
            danger_prob = float(prediction_proba[0][0])
            safe_prob = float(prediction_proba[0][1])
            
            # Map to class labels
            is_danger = int(prediction == 0)
            class_label = "DANGER 🔴" if prediction == 0 else "SAFE 🟢"
            
            print(f"📊 Prediction: {class_label}")
            print(f"📊 Confidence: {confidence:.4f}")
            print(f"📊 Danger Probability: {danger_prob:.4f}")
            print(f"📊 Safe Probability: {safe_prob:.4f}")
            
            return {
                'status': 'success',
                'is_danger': is_danger,
                'prediction': int(prediction),
                'confidence': confidence,
                'danger_probability': danger_prob,
                'safe_probability': safe_prob,
                'class_label': class_label
            }
            
        except Exception as e:
            print(f"❌ Prediction error: {str(e)}")
            return {
                'status': 'error',
                'message': f'Prediction failed: {str(e)}'
            }

def test_processor():
    """Test the processor with a sample file"""
    import sys
    
    # Find model and scaler
    model_path = os.path.join('modals', 'audio_danger_detection_cnn.h5')
    scaler_path = os.path.join('modals', 'feature_scaler.pkl')
    
    if not os.path.exists(model_path):
        print("❌ Model file not found!")
        print(f"📁 Looking in: {os.path.abspath('modals')}")
        if os.path.exists('modals'):
            print("📄 Files in modals folder:")
            for f in os.listdir('modals'):
                print(f"   - {f}")
        return
    
    try:
        processor = AudioProcessor(model_path, scaler_path)
        
        # Get audio file path
        if len(sys.argv) > 1:
            # Join all arguments to handle paths with spaces
            audio_file = ' '.join(sys.argv[1:])
        else:
            # Look for test files
            test_files = ['test.wav', 'audio.wav', 'sample.wav', 'test.mp3']
            audio_file = None
            for tf in test_files:
                if os.path.exists(tf):
                    audio_file = tf
                    break
            
            if not audio_file:
                audio_file = input("Enter audio file path: ").strip()
        
        if os.path.exists(audio_file):
            print(f"\n🔍 Testing with: {audio_file}")
            result = processor.predict_danger(audio_file)
            print("\n" + "="*50)
            print("📊 FINAL RESULT:")
            print("="*50)
            for key, value in result.items():
                print(f"{key}: {value}")
        else:
            print(f"❌ File not found: {audio_file}")
            print(f"📁 Current directory: {os.getcwd()}")
            print("📄 Available files:")
            for f in os.listdir('.'):
                if f.endswith(('.wav', '.mp3', '.ogg')):
                    print(f"   - {f}")
            
    except Exception as e:
        print(f"❌ Test failed: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_processor()