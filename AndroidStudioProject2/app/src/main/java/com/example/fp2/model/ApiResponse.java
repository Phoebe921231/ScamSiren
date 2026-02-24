package com.example.fp2.model;

import com.google.gson.JsonElement;
import java.util.List;

public class ApiResponse {
    public boolean is_scam;
    public String risk;
    public List<String> reasons;
    public List<String> advices;
    public String detected_text;
    public String source;
    public JsonElement analysis;
    public String text;
    public Meta meta;
    public java.util.List<String> scam_type;
    public static class Meta{
        public String asr_backend;
        public String ollama_model;
        public String request_id;
    }
}

