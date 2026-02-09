import React, { useState } from 'react';

const VideoUpload = () => {
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [message, setMessage] = useState('');

    const handleFileChange = (e) => {
        setFile(e.target.files[0]);
        setMessage('');
    };

    const handleUpload = async (e) => {
        e.preventDefault();
        if (!file) {
            setMessage('Please select a file first.');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setUploading(true);
        setMessage('');

        try {
            const response = await fetch('http://localhost:8081/api/upload-video', {
                method: 'POST',
                body: formData,
            });

            const result = await response.json();

            if (response.ok && result.success) {
                setMessage(`Upload successful! File: ${result.fileName}`);
                setFile(null);
                // Reset file input
                document.getElementById('video-upload-input').value = '';
            } else {
                setMessage(`Upload failed: ${result.error || 'Unknown error'}`);
            }
        } catch (error) {
            console.error('Error uploading video:', error);
            setMessage('Error uploading video. Check console for details.');
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="video-upload-container" style={{ padding: '20px', border: '1px solid #ccc', borderRadius: '5px' }}>
            <h2>Upload Video</h2>
            <form onSubmit={handleUpload}>
                <div style={{ marginBottom: '15px' }}>
                    <input 
                        type="file" 
                        id="video-upload-input"
                        accept="video/*" 
                        onChange={handleFileChange} 
                        disabled={uploading}
                    />
                </div>
                <button type="submit" disabled={uploading || !file}>
                    {uploading ? 'Uploading...' : 'Upload Video'}
                </button>
            </form>
            {message && <p style={{ marginTop: '10px', color: message.includes('failed') || message.includes('Error') ? 'red' : 'green' }}>{message}</p>}
        </div>
    );
};

export default VideoUpload;
