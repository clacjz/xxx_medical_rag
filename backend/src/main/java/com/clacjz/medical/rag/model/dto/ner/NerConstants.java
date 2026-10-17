package com.clacjz.medical.rag.model.dto.ner;

/**
 * NER实体类型常量
 */
public final class NerConstants {

    private NerConstants() {}

    public static final String DISEASE = "DISEASE";
    public static final String DRUG = "DRUG";
    public static final String SYMPTOM = "SYMPTOM";
    public static final String TEST = "TEST";
    public static final String TREATMENT = "TREATMENT";
    public static final String ANATOMY = "ANATOMY";
    public static final String ORGANIZATION = "ORG";
    public static final String REGION = "REGION";

    public static final String[] DEFAULT_TYPES = {DISEASE, DRUG, SYMPTOM, ORGANIZATION};
    public static final String[] ALL_TYPES = {DISEASE, DRUG, SYMPTOM, TEST, TREATMENT, ANATOMY, ORGANIZATION, REGION};
}
