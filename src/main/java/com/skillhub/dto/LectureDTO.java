package com.skillhub.dto;

public class LectureDTO {
    private Long id;
    private String title;
    private String videoUrl;
    private Integer durationSeconds;
    private boolean isPreview;
    private int position;

    public LectureDTO() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isPreview() {
        return isPreview;
    }

    public void setPreview(boolean preview) {
        isPreview = preview;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
    public String getEmbedUrl() {
        if (videoUrl == null)
            return null;
        if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
            String videoId = "";
            if (videoUrl.contains("youtu.be/")) {
                videoId = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
                if (videoId.contains("?")) {
                    videoId = videoId.substring(0, videoId.indexOf("?"));
                }
            } else if (videoUrl.contains("v=")) {
                int index = videoUrl.indexOf("v=");
                videoId = videoUrl.substring(index + 2);
                if (videoId.contains("&")) {
                    videoId = videoId.substring(0, videoId.indexOf("&"));
                }
            } else if (videoUrl.contains("embed/")) {
                videoId = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
                if (videoId.contains("?")) {
                    videoId = videoId.substring(0, videoId.indexOf("?"));
                }
            }

            if (!videoId.isEmpty()) {
                return "https://www.youtube.com/embed/" + videoId;
            }
        }
        return videoUrl;
    }
}
