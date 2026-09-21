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
     * PC ka separate recordings folder
     */
    private final Path storageLocation =
            Paths.get("C:\\Recordings");

    /*
     * Active recordings
     *
     * Example:
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
     * MANUAL WAV UPLOAD
     * =========================================================
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
             * Uploaded WAV ko PC folder mein save karna
             */
            Files.write(filePath, audioData);

            /*
             * Database object
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

            /*
             * Actual audio DB mein
             */
            callAudio.setAudioData(audioData);

            return callAudioRepository.save(callAudio);

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to save uploaded audio",
                    e
            );
        }
    }


    /*
     * =========================================================
     * START RTP RECORDING
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
                createKey(callId, audioType);

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

        activeRecordings.put(key, session);

        System.out.println(
                "[Audio] Recording started: "
                        + key
        );
    }


    /*
     * =========================================================
     * RECEIVE AUDIO FROM RTP
     * =========================================================
     */
    @Override
    public void appendAudio(
            String callId,
            String audioType,
            byte[] audioData) {

        String key =
                createKey(callId, audioType);

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

        session.append(audioData);
    }


    /*
     * =========================================================
     * STOP RECORDING
     * =========================================================
     */
    @Override
    public CallAudio stopRecording(
            String callId,
            String audioType) {

        String key =
                createKey(callId, audioType);

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
             * Raw audio ko WAV file mein convert karna
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

            /*
             * Final WAV ko read karna
             */
            byte[] finalWavBytes =
                    Files.readAllBytes(wavPath);

            LocalDateTime endTime =
                    LocalDateTime.now();

            /*
             * Database object
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
             * Final WAV DB mein save
             */
            callAudio.setAudioData(
                    finalWavBytes
            );

            CallAudio saved =
                    callAudioRepository.save(
                            callAudio
                    );

            System.out.println(
                    "[Audio] Recording completed: "
                            + key
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
     * GET AUDIO BY CALL ID
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
     * GET AUDIO BY DATABASE ID
     * =========================================================
     */
    @Override
    public CallAudio getAudioById(Long id) {

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