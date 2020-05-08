package de.intranda.goobi.plugins.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class Config {

    private String defaultPublicationType;

    List<DocumentType> documentTypeList = new ArrayList<>();

    /**
     * loads the &lt;config&gt; block from xml file
     * 
     * @param xmlConfig
     */

    public Config(SubnodeConfiguration xmlConfig) {

        defaultPublicationType = xmlConfig.getString("/defaultPublicationType", "Monograph");

        List<HierarchicalConfiguration> recordTypeList = xmlConfig.configurationsAt("/recordType");
        for (HierarchicalConfiguration recordType : recordTypeList) {
            DocumentType type = new DocumentType(recordType.getString("@field"), recordType.getString("@expectedValue"),
                    recordType.getString("@docType"), recordType.getString("@matchType", "exact"));
            documentTypeList.add(type);
        }
    }

    @Data
    @AllArgsConstructor
    public class DocumentType {
        private String jsonFieldName;
        private String fieldValue;
        private String publicationType;
        private String matchType;
    }
}
