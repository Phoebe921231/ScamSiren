package com.example.fp2.model;

import java.util.List;

public class ApiResponse {
    public boolean is_scam;
    public String risk;
    public List<String> reasons;
    public List<String> advices;
    public Analysis analysis;

    public static class Analysis {
        public double confidence;
        public List<String> matched_categories;
        public List<String> actions_requested;
    }
}

