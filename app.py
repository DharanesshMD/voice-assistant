import os
from flask import Flask, request, jsonify
import whisper

app = Flask(__name__)

# Load the Whisper model
model = whisper.load_model("base")

@app.route('/transcribe', methods=['POST'])
def transcribe():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    # Get the uploaded file
    audio_file = request.files['file']
    print(f"Received file: {audio_file.filename}")

    # Save the file to a temporary location
    file_path = os.path.join("temp", audio_file.filename)
    os.makedirs("temp", exist_ok=True)
    audio_file.save(file_path)

    try:
        # Transcribe the audio file using Whisper
        result = model.transcribe(file_path)
        transcription = result["text"]
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        # Clean up the temporary file
        os.remove(file_path)

    # Return the transcription as a JSON response
    return jsonify({"transcription": transcription})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
