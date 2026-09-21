package com.example.audio.service;

import com.example.audio.entity.CallAudio;

import java.time.LocalDateTime;
import java.util.List;

public interface AudioService {

    // Manual WAV upload
    CallAudio saveUploadedRecording(
            String callId,
            String audioType,
            byte[] audioData,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    // RTP recording start
    void startRecording(
            String callId,
            String audioType,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian
    );

    // RTP audio packets
    void appendAudio(
            String callId,
            String audioType,
            byte[] audioData
    );

    // Recording stop
    CallAudio stopRecording(
            String callId,
            String audioType
    );

    List<CallAudio> getAudioByCallId(String callId);

    CallAudio getAudioById(Long id);
}