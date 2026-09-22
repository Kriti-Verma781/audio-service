package com.example.audio.service;

import com.example.audio.entity.CallAudio;
import com.example.audio.repository.CallAudioRepository;

import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioServiceImpl implements AudioService {

    private final CallAudioRepository callAudioRepository;

    /*
     * =========================================================
     * PC RECORDING STORAGE
     * =========================================================
     *
     * Actual audio files yahan save hongi.
     *
     * C:\Recordings\callee
     * C:\Recordings\full-call
     */
    private final Path storageLocation =
            Paths.get("C:\\Recordings");

    /*
     * =========================================================
     * ACTIVE RECORDINGS
     * =========================================================
     *
     * Example:
     *
     * CALL001_CALLEE
     * CALL001_FULL_CALL
     */
    private final Map<String, RecordingSession> activeRecordings =
            new ConcurrentHashMap<>();

    public AudioServiceImpl(
            CallAudioRepository callAudioRepository) {

        this.callAudioRepository =
                callAudioRepository;
    }

    /*
     * =========================================================
     * 1. MANUAL WAV UPLOAD
     * =========================================================
     *
     * Testing ke liye.
     *
     * Actual WAV file PC ke folder mein save hogi.
     * MySQL mein sirf file path save hoga.
     */
    @Override
    public CallAudio saveUploadedRecording(
            String callId,
            String audioType,
            byte[] audioData,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        validateAudioType(audioType);

        try {

            String folderName =
                    getFolderName(audioType);

            Path folder =
                    storageLocation.resolve(folderName);

            Files.createDirectories(folder);

            String timestamp =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "yyyyMMdd_HHmmss"
                                    )
                            );

            String fileName =
                    callId + "_"
                            + audioType.toLowerCase()
                            + "_"
                            + timestamp
                            + ".wav";

            Path filePath =
                    folder.resolve(fileName);

            /*
             * Actual recording PC ke folder mein save
             */
            Files.write(
                    filePath,
                    audioData
            );

            /*
             * Database object
             *
             * Yahan actual audio save nahi ho raha.
             * Sirf file ka path save hoga.
             */
            CallAudio callAudio =
                    new CallAudio();

            callAudio.setCallId(callId);

            callAudio.setAudioType(
                    audioType.toUpperCase()
            );

            callAudio.setFileName(fileName);

            callAudio.setFilePath(
                    filePath.toString()
            );

            callAudio.setStartTime(startTime);

            callAudio.setEndTime(endTime);

            return callAudioRepository.save(
                    callAudio
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to save uploaded audio",
                    e
            );
        }
    }

    /*
     * =========================================================
     * 2. START RTP RECORDING
     * =========================================================
     */
    @Override
    public void startRecording(
            String callId,
            String audioType,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian) {

        validateAudioType(audioType);

        String key =
                createKey(
                        callId,
                        audioType
                );

        if (activeRecordings.containsKey(key)) {

            throw new IllegalStateException(
                    "Recording already active for "
                            + key
            );
        }

        AudioFormat format =
                new AudioFormat(
                        sampleRate,
                        sampleSizeInBits,
                        channels,
                        signed,
                        bigEndian
                );

        RecordingSession session =
                new RecordingSession(
                        callId,
                        audioType.toUpperCase(),
                        format
                );

        activeRecordings.put(
                key,
                session
        );

        System.out.println(
                "[Audio] Recording started: "
                        + key
        );
    }

    /*
     * =========================================================
     * 3. RECEIVE AUDIO FROM RTP
     * =========================================================
     *
     * RTP module audio bytes yahan bhejega.
     */
    @Override
    public void appendAudio(
            String callId,
            String audioType,
            byte[] audioData) {

        String key =
                createKey(
                        callId,
                        audioType
                );

        RecordingSession session =
                activeRecordings.get(key);

        if (session == null) {

            throw new IllegalStateException(
                    "No active recording found for "
                            + key
            );
        }

        if (audioData == null
                || audioData.length == 0) {

            return;
        }

        /*
         * RTP se aaye audio bytes recording
         * ke buffer mein add honge.
         */
        session.append(audioData);
    }

    /*
     * =========================================================
     * 4. STOP RECORDING
     * =========================================================
     *
     * Recording stop hone par:
     *
     * 1. RTP ke raw audio bytes milenge
     * 2. Unse WAV file banegi
     * 3. WAV file PC par save hogi
     * 4. MySQL mein sirf path save hoga
     */
    @Override
    public CallAudio stopRecording(
            String callId,
            String audioType) {

        String key =
                createKey(
                        callId,
                        audioType
                );

        RecordingSession session =
                activeRecordings.remove(key);

        if (session == null) {

            throw new IllegalStateException(
                    "No active recording found for "
                            + key
            );
        }

        try {

            byte[] rawAudio =
                    session.getAudioBytes();

            if (rawAudio.length == 0) {

                throw new IllegalStateException(
                        "No audio data received for "
                                + key
                );
            }

            String folderName =
                    getFolderName(audioType);

            Path folder =
                    storageLocation.resolve(folderName);

            Files.createDirectories(folder);

            String timestamp =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "yyyyMMdd_HHmmss"
                                    )
                            );

            String fileName =
                    callId + "_"
                            + audioType.toLowerCase()
                            + "_"
                            + timestamp
                            + ".wav";

            Path wavPath =
                    folder.resolve(fileName);

            /*
             * Raw RTP audio ko WAV file mein convert
             */
            try (
                    ByteArrayInputStream input =
                            new ByteArrayInputStream(
                                    rawAudio
                            );

                    AudioInputStream audioInputStream =
                            new AudioInputStream(
                                    input,
                                    session.getFormat(),
                                    rawAudio.length
                                            / session.getFormat()
                                            .getFrameSize()
                            )
            ) {

                AudioSystem.write(
                        audioInputStream,
                        AudioFileFormat.Type.WAVE,
                        wavPath.toFile()
                );
            }

            LocalDateTime endTime =
                    LocalDateTime.now();

            /*
             * Database object
             *
             * IMPORTANT:
             * Actual WAV audio DB mein nahi ja rahi.
             * Sirf file ka path save ho raha hai.
             */
            CallAudio callAudio =
                    new CallAudio();

            callAudio.setCallId(callId);

            callAudio.setAudioType(
                    audioType.toUpperCase()
            );

            callAudio.setFileName(fileName);

            callAudio.setFilePath(
                    wavPath.toString()
            );

            callAudio.setStartTime(
                    session.getStartTime()
            );

            callAudio.setEndTime(
                    endTime
            );

            /*
             * MySQL mein metadata + file path save
             */
            CallAudio saved =
                    callAudioRepository.save(
                            callAudio
                    );

            System.out.println(
                    "[Audio] Recording completed: "
                            + key
                            + " | File: "
                            + wavPath
                            + " | DB ID: "
                            + saved.getId()
            );

            return saved;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to finalize recording",
                    e
            );
        }
    }

    /*
     * =========================================================
     * 5. GET AUDIO BY CALL ID
     * =========================================================
     */
    @Override
    public List<CallAudio> getAudioByCallId(
            String callId) {

        return callAudioRepository
                .findByCallId(callId);
    }

    /*
     * =========================================================
     * 6. GET AUDIO BY DATABASE ID
     * =========================================================
     */
    @Override
    public CallAudio getAudioById(
            Long id) {

        return callAudioRepository
                .findById(id)
                .orElseThrow(
                        () -> new RuntimeException(
                                "Audio not found with id: "
                                        + id
                        )
                );
    }

    /*
     * =========================================================
     * CREATE RECORDING KEY
     * =========================================================
     */
    private String createKey(
            String callId,
            String audioType) {

        return callId
                + "_"
                + audioType.toUpperCase();
    }

    /*
     * =========================================================
     * VALIDATE AUDIO TYPE
     * =========================================================
     */
    private void validateAudioType(
            String audioType) {

        if (!"CALLEE".equalsIgnoreCase(audioType)
                && !"FULL_CALL".equalsIgnoreCase(audioType)) {

            throw new IllegalArgumentException(
                    "audioType must be CALLEE or FULL_CALL"
            );
        }
    }

    /*
     * =========================================================
     * GET FOLDER NAME
     * =========================================================
     */
    private String getFolderName(
            String audioType) {

        if ("CALLEE".equalsIgnoreCase(audioType)) {

            return "callee";
        }

        if ("FULL_CALL".equalsIgnoreCase(audioType)) {

            return "full-call";
        }

        throw new IllegalArgumentException(
                "Invalid audio type"
        );
    }

    /*
     * =========================================================
     * RECORDING SESSION
     * =========================================================
     */
    private static class RecordingSession {

        private final String callId;

        private final String audioType;

        private final AudioFormat format;

        private final LocalDateTime startTime;

        private final ByteArrayOutputStream audioBuffer =
                new ByteArrayOutputStream();

        public RecordingSession(
                String callId,
                String audioType,
                AudioFormat format) {

            this.callId = callId;

            this.audioType = audioType;

            this.format = format;

            this.startTime =
                    LocalDateTime.now();
        }

        public synchronized void append(
                byte[] data) {

            audioBuffer.write(
                    data,
                    0,
                    data.length
            );
        }

        public byte[] getAudioBytes() {

            return audioBuffer.toByteArray();
        }

        public AudioFormat getFormat() {

            return format;
        }

        public LocalDateTime getStartTime() {

            return startTime;
        }
    }
}