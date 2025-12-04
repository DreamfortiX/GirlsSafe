# Audio Danger Detection Server

This server provides an API for detecting dangerous sounds in audio files using a pre-trained deep learning model.

## Prerequisites

- Python 3.8 or higher
- pip (Python package manager)

## Setup

1. **Create a virtual environment (recommended):**

   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

2. **Install dependencies:**

   ```bash
   pip install -r requirements.txt
   ```

   Note: On some systems, you might need to install additional system dependencies for `librosa` and `soundfile`:
   - On Ubuntu/Debian: `sudo apt-get install libsndfile1 ffmpeg`
   - On macOS: `brew install libsndfile ffmpeg`

3. **Place your model file:**
   - Ensure your model file `audio_danger_detection_cnn.h5` is in the `backend` directory.

## Running the Server

1. **Start the Flask development server:**

   ```bash
   cd backend
   python main.py
   ```

2. The server will start on `http://0.0.0.0:5000/`

## API Endpoints

- `GET /` - Server status and available endpoints
- `GET /health` - Health check
- `POST /upload` - Upload an audio file for analysis

### Uploading Audio

Send a POST request to `/upload` with a multipart form containing an audio file:

```bash
curl -X POST -F "file=@/path/to/your/audio.3gp" http://localhost:5000/upload
```

### Response Format

```json
{
  "status": "success",
  "filename": "audio.3gp",
  "message": "File processed successfully",
  "analysis": {
    "status": "success",
    "is_danger": false,
    "confidence": 0.15,
    "threshold": 0.5
  }
}
```

## Troubleshooting

- If you encounter issues with audio processing, check the server logs for detailed error messages.
- Ensure the audio file format is supported (wav, mp3, ogg, flac, m4a, 3gpp).
- The maximum file size is 16MB by default (configurable in `main.py`).

## License

[Your License Here]
