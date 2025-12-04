# audio_processor.py
import os
import numpy as np
import librosa
import tensorflow as tf
from tensorflow.keras.models import load_model
import joblib
import soundfile as sf
import warnings
warnings.filterwarnings('ignore')

class AudioProcessor:
    def __init__(self, model_path, scaler_path=None):
        """
        Initialize the audio danger detector optimized for your trained model.
        
        Args:
            model_path: Path to the model file (.h5 for CNN, .pkl for Random Forest)
            scaler_path: Path to the feature scaler (optional, mainly for RF)
        """
        print(f"🔊 Initializing Audio Processor...")
        
        # Check if model exists
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")
        
        # Determine model type
        self.model_type = None
        if model_path.endswith('.h5') or model_path.endswith('.keras'):
            self.model_type = 'cnn'
        elif model_path.endswith('.pkl'):
            self.model_type = 'rf'
        else:
            raise ValueError(f"Unknown model format: {model_path}")
        
        print(f"📊 Detected model type: {self.model_type.upper()}")
        
        # Load model based on type
        if self.model_type == 'cnn':
            try:
                # Suppress TensorFlow logging
                tf.get_logger().setLevel('ERROR')
                
                # Load CNN model
                self.model = load_model(model_path, compile=False)
                
                # Compile with minimal settings for prediction
                self.model.compile(
                    optimizer='adam',
                    loss='categorical_crossentropy',
                    metrics=['accuracy']
                )
                
                print(f"✅ CNN model loaded from: {model_path}")
                
                # Try to get input shape for debugging
                try:
                    input_shape = self.model.input_shape
                    print(f"📊 Model expects input shape: {input_shape}")
                except:
                    print("ℹ️ Could not determine model input shape")
                    
            except Exception as e:
                raise ValueError(f"Failed to load CNN model: {str(e)}")
                
        elif self.model_type == 'rf':
            try:
                # Load Random Forest model
                self.model = joblib.load(model_path)
                print(f"✅ Random Forest model loaded from: {model_path}")
                
                # Check if model has predict_proba
                if not hasattr(self.model, 'predict_proba'):
                    print("⚠️  Warning: Random Forest model may not support probability predictions")
                    
            except Exception as e:
                raise ValueError(f"Failed to load Random Forest model: {str(e)}")
        
        # Load scaler if provided
        if scaler_path and os.path.exists(scaler_path):
            try:
                self.scaler = joblib.load(scaler_path)
                print(f"✅ Scaler loaded from: {scaler_path}")
            except Exception as e:
                print(f"⚠️  Could not load scaler: {str(e)}")
                self.scaler = None
        else:
            self.scaler = None
            print("ℹ️ No scaler provided or found")
        
        # CRITICAL: Set audio processing parameters to match training EXACTLY
        self.target_sr = 22050      # Model expects 22,050 Hz (from your training)
        self.duration = 4.0         # Model expects 4 seconds (from your training)
        self.n_mfcc = 13            # MUST be 13 MFCC coefficients (from your training)
        
        print(f"✅ Processor initialized successfully!")
        print(f"   Model Type: {self.model_type.upper()}")
        print(f"   Expected SR: {self.target_sr} Hz")
        print(f"   Expected Duration: {self.duration} seconds")
        print(f"   MFCC Coefficients: {self.n_mfcc}")

    def extract_features(self, audio_path):
        """
        Extract MFCC features from audio file EXACTLY like in training.
        
        Args:
            audio_path: Path to audio file
            
        Returns:
            tuple: (features, success) where features is numpy array or None
        """
        try:
            print(f"📊 Loading audio: {os.path.basename(audio_path)}")
            
            # Get file info
            file_size = os.path.getsize(audio_path)
            print(f"   File size: {file_size} bytes")
            
            # Load audio with error handling
            try:
                # First try librosa
                audio, sr = librosa.load(audio_path, sr=None, duration=self.duration)
                print(f"✅ Loaded with librosa - Original SR: {sr} Hz, Samples: {len(audio)}")
            except Exception as librosa_error:
                print(f"⚠️ Librosa failed: {librosa_error}")
                try:
                    # Try soundfile as backup (better for WAV files)
                    audio, sr = sf.read(audio_path)
                    
                    # Convert to mono if stereo
                    if len(audio.shape) > 1:
                        audio = np.mean(audio, axis=1)
                        print(f"   Converted stereo to mono")
                    
                    # Trim to duration if needed
                    max_samples = int(sr * self.duration)
                    if len(audio) > max_samples:
                        audio = audio[:max_samples]
                        print(f"   Trimmed to {max_samples} samples for {self.duration}s")
                        
                    print(f"✅ Loaded with soundfile - Original SR: {sr} Hz, Samples: {len(audio)}")
                except Exception as sf_error:
                    raise ValueError(f"Failed to load audio with any method: {sf_error}")
            
            # CRITICAL: Resample to 22,050 Hz if needed
            if sr != self.target_sr:
                print(f"🔄 Resampling from {sr} Hz to {self.target_sr} Hz")
                audio = librosa.resample(audio, orig_sr=sr, target_sr=self.target_sr)
                sr = self.target_sr
            
            # Ensure exact length for 4 seconds
            target_length = int(self.target_sr * self.duration)
            if len(audio) < target_length:
                padding = target_length - len(audio)
                audio = np.pad(audio, (0, padding), mode='constant')
                print(f"➕ Padded with {padding} zeros")
            elif len(audio) > target_length:
                audio = audio[:target_length]
                print(f"✂️ Trimmed to {target_length} samples")
            
            print(f"📊 Final audio: {len(audio)} samples at {sr} Hz")
            print(f"   Audio stats - Min: {np.min(audio):.4f}, Max: {np.max(audio):.4f}, Mean: {np.mean(audio):.4f}")
            
            # Extract MFCC features EXACTLY like in training
            print("🎵 Extracting MFCC features...")
            mfccs = librosa.feature.mfcc(
                y=audio,
                sr=sr,
                n_mfcc=self.n_mfcc,  # MUST BE 13
                n_fft=2048,
                hop_length=512
            )
            
            print(f"📊 Raw MFCC shape: {mfccs.shape}")
            
            # CRITICAL: Calculate mean and std across time axis (EXACTLY like training)
            mfccs_mean = np.mean(mfccs, axis=1)
            mfccs_std = np.std(mfccs, axis=1)
            
            print(f"📊 MFCC Mean shape: {mfccs_mean.shape}")
            print(f"📊 MFCC Std shape: {mfccs_std.shape}")
            
            # Combine mean and std (26 features: 13 mean + 13 std)
            features = np.hstack([mfccs_mean, mfccs_std])
            
            print(f"✅ Features extracted: {features.shape}")
            
            return features, True
            
        except Exception as e:
            print(f"❌ Error extracting features: {str(e)}")
            import traceback
            traceback.print_exc()
            return None, False

    def preprocess_for_model(self, features):
        """
        Preprocess features for the specific model type.
        
        Args:
            features: Raw features from extract_features()
            
        Returns:
            numpy.ndarray: Features ready for model prediction
        """
        if self.model_type == 'cnn':
            # Reshape for CNN: (1, 13, 2, 1)
            # 13 MFCCs × 2 frames (mean+std) × 1 channel
            features_reshaped = features.reshape(1, 13, 2, 1)
            print(f"📊 CNN input shape: {features_reshaped.shape}")
            return features_reshaped
            
        elif self.model_type == 'rf':
            # For Random Forest, use flat features
            features_flat = features.reshape(1, -1)
            
            # Apply scaling if scaler exists
            if self.scaler is not None:
                features_flat = self.scaler.transform(features_flat)
                print("✅ Features scaled for Random Forest")
            else:
                print("⚠️  No scaler for Random Forest - using raw features")
            
            print(f"📊 RF input shape: {features_flat.shape}")
            return features_flat
            
        else:
            raise ValueError(f"Unknown model type: {self.model_type}")

    def predict(self, audio_path):
        """
        Make prediction on audio file.
        
        Args:
            audio_path: Path to audio file
            
        Returns:
            dict: Prediction results or None if failed
        """
        print(f"\n🎯 Analyzing audio: {os.path.basename(audio_path)}")
        
        # Extract features
        features, success = self.extract_features(audio_path)
        if not success or features is None:
            print("❌ Failed to extract features")
            return None
        
        # Preprocess for model
        model_input = self.preprocess_for_model(features)
        
        # Make prediction based on model type
        try:
            if self.model_type == 'cnn':
                # CNN prediction
                prediction_proba = self.model.predict(model_input, verbose=0)
                prediction = np.argmax(prediction_proba, axis=1)[0]
                confidence = np.max(prediction_proba)
                probabilities = prediction_proba[0].tolist()
                
            elif self.model_type == 'rf':
                # Random Forest prediction
                prediction = self.model.predict(model_input)[0]
                
                # Try to get probabilities
                try:
                    prediction_proba = self.model.predict_proba(model_input)[0]
                    confidence = np.max(prediction_proba)
                    probabilities = prediction_proba.tolist()
                except:
                    # Fallback if predict_proba not available
                    confidence = 1.0 if prediction == self.model.predict(model_input)[0] else 0.0
                    probabilities = [1.0 - confidence, confidence] if prediction == 1 else [confidence, 1.0 - confidence]
            else:
                raise ValueError(f"Unknown model type: {self.model_type}")
            
            # Get class label
            class_label = "SAFE 🟢" if prediction == 1 else "DANGER 🔴"
            
            # Create result dictionary
            result = {
                'prediction': int(prediction),
                'class_label': class_label,
                'confidence': float(confidence),
                'danger_probability': float(probabilities[0]) if len(probabilities) > 0 else 0.0,
                'safe_probability': float(probabilities[1]) if len(probabilities) > 1 else 0.0,
                'probabilities': probabilities,
                'model_type': self.model_type,
                'features_shape': list(model_input.shape)
            }
            
            print(f"✅ Prediction complete:")
            print(f"   📊 Classification: {class_label}")
            print(f"   🎯 Confidence: {confidence:.4f}")
            print(f"   🔴 Danger Probability: {result['danger_probability']:.4f}")
            print(f"   🟢 Safe Probability: {result['safe_probability']:.4f}")
            print(f"   🤖 Model Type: {self.model_type.upper()}")
            
            return result
            
        except Exception as e:
            print(f"❌ Prediction error: {str(e)}")
            import traceback
            traceback.print_exc()
            return None

    def predict_danger(self, audio_path):
        """
        Alias for predict method for Flask server compatibility.
        """
        return self.predict(audio_path)

# Quick test function
def test_processor():
    """Test the processor with a sample file"""
    import sys
    
    # Try to find model
    model_path = None
    possible_paths = [
        os.path.join('modals', 'audio_danger_detection_cnn.h5'),
        'audio_danger_detection_cnn.h5',
        os.path.join('modals', 'best_audio_detection_model.h5'),
        'best_audio_detection_model.h5'
    ]
    
    for path in possible_paths:
        if os.path.exists(path):
            model_path = path
            break
    
    if not model_path:
        print("❌ No model file found!")
        print("Please place a model file in the modals/ folder.")
        return
    
    print(f"📁 Using model: {model_path}")
    
    try:
        # Try to find scaler
        scaler_path = None
        scaler_paths = [
            os.path.join('modals', 'feature_scaler.pkl'),
            'feature_scaler.pkl'
        ]
        
        for path in scaler_paths:
            if os.path.exists(path):
                scaler_path = path
                break
        
        processor = AudioProcessor(model_path, scaler_path)
        
        # Test with file
        if len(sys.argv) > 1:
            audio_file = sys.argv[1]
        else:
            print("\nPlease provide an audio file path for testing")
            audio_file = input("Audio file path: ").strip()
        
        if os.path.exists(audio_file):
            result = processor.predict(audio_file)
            if result:
                print(f"\n🎯 Test Result: {result}")
            else:
                print("❌ Test failed: No result returned")
        else:
            print(f"❌ File not found: {audio_file}")
            
    except Exception as e:
        print(f"❌ Test failed: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_processor()