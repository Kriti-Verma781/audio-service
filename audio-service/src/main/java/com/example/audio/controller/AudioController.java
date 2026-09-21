package com.example.audio.controller;

import com.example.audio.entity.CallAudio;
import com.example.audio.service.AudioService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.IOException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audioService;


    public AudioController(
            AudioService audioService) {

        this.audioService =
                audioService;
    }


    /*
     * =========================================================
     * 1. MANUAL WAV UPLOAD
     * =========================================================
     *
     * Testing ke liye.
     */
    @PostMapping(
            value = "/recording",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CallAudio uploadRecording(

            @RequestParam String callId,

            @RequestParam String audioType,

            @RequestParam MultipartFile file)

            throws IOException {


        LocalDateTime startTime =
                LocalDateTime.now();


        byte[] audioData =
                file.getBytes();


        float sampleRate = 8000;

        int channels = 1;

        int sampleSizeInBits = 8;

        boolean signed = false;

        boolean bigEndian = false;


        /*
         * Uploaded WAV ka actual format read karna
         */
        try (
                AudioInputStream audioInputStream =
                        AudioSystem.getAudioInputStream(
                                file.getInputStream()
                        )
        ) {

            AudioFormat format =
                    audioInputStream.getFormat();


            sampleRate =
                    format.getSampleRate();

            channels =
                    format.getChannels();

            sampleSizeInBits =
                    format.getSampleSizeInBits();

            signed =
                    format.getEncoding()
                            == AudioFormat.Encoding.PCM_SIGNED;

            bigEndian =
                    format.isBigEndian();


        } catch (Exception e) {

            System.out.println(
                    "[Audio] Could not read WAV format: "
                            + e.getMessage()
            );
        }


        LocalDateTime endTime =
                LocalDateTime.now();


        return audioService.saveUploadedRecording(

                callId,

                audioType,

                audioData,

                sampleRate,

                channels,

                sampleSizeInBits,

                signed,

                bigEndian,

                startTime,

                endTime
        );
    }


    /*
     * =========================================================
     * 2. START RTP RECORDING
     * =========================================================
     */
    @PostMapping("/recording/start")
    public String startRecording(

            @RequestParam String callId,

            @RequestParam String audioType,

            @RequestParam float sampleRate,

            @RequestParam int channels,

            @RequestParam int sampleSizeInBits,

            @RequestParam(
                    defaultValue = "true"
            )
            boolean signed,

            @RequestParam(
                    defaultValue = "false"
            )
            boolean bigEndian) {


        audioService.startRecording(

                callId,

                audioType,

                sampleRate,

                channels,

                sampleSizeInBits,

                signed,

                bigEndian
        );


        return "Recording started for "
                + callId
                + " - "
                + audioType;
    }


    /*
     * =========================================================
     * 3. RECEIVE AUDIO DATA FROM RTP
     * =========================================================
     */
    @PostMapping(
            value = "/recording/audio",
            consumes =
                    MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public String receiveAudio(

            @RequestParam String callId,

            @RequestParam String audioType,

            @RequestBody byte[] audioData) {


        audioService.appendAudio(

                callId,

                audioType,

                audioData
        );


        return "Audio data received";
    }


    /*
     * =========================================================
     * 4. STOP RTP RECORDING
     * =========================================================
     */
    @PostMapping("/recording/stop")
    public CallAudio stopRecording(

            @RequestParam String callId,

            @RequestParam String audioType) {


        return audioService.stopRecording(

                callId,

                audioType
        );
    }


    /*
     * =========================================================
     * 5. GET AUDIO BY CALL ID
     * =========================================================
     */
    @GetMapping("/call/{callId}")
    public List<CallAudio> getAudioByCallId(

            @PathVariable String callId) {


        return audioService
                .getAudioByCallId(callId);
    }


    /*
     * =========================================================
     * 6. PLAY AUDIO FROM DATABASE
     * =========================================================
     */
    @GetMapping("/play/{id}")
    public ResponseEntity<byte[]> playAudio(

            @PathVariable Long id) {


        CallAudio audio =
                audioService.getAudioById(id);


        return ResponseEntity.ok()

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,

                        "inline; filename=\""
                                + audio.getFileName()
                                + "\""
                )

                .contentType(
                        MediaType.parseMediaType(
                                "audio/wav"
                        )
                )

                .body(
                        audio.getAudioData()
                );
    }
}