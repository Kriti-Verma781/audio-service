package com.example.audio.repository;

import com.example.audio.entity.CallAudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallAudioRepository
        extends JpaRepository<CallAudio, Long> {

    List<CallAudio> findByCallId(String callId);
}