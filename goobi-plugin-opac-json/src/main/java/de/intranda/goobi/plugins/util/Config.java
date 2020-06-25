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

    private List<DocumentType> documentTypeList = new ArrayList<>();

    private List<MetadataField> metadataFieldList = new ArrayList<>();

    private List<PersonField> personFieldList = new ArrayList<>();

    private String username;
    private String password;
    private String loginUrl;
    private String sessionid;
    private String headerParameter;

    /**
     * loads the &lt;config&gt; block from xml file
     * 
     * @param xmlConfig
     */

    public Config(SubnodeConfiguration xmlConfig) {

        defaultPublicationType = xmlConfig.getString("/defaultPublicationType", null);

        List<HierarchicalConfiguration> recordTypeList = xmlConfig.configurationsAt("/recordType");
        for (HierarchicalConfiguration recordType : recordTypeList) {
            DocumentType type =
                    new DocumentType(recordType.getString("@field"), recordType.getString("@docType"), recordType.getString("@anchorType", null));
            documentTypeList.add(type);
        }

        List<HierarchicalConfiguration> metadataList = xmlConfig.configurationsAt("/metadata");
        for (HierarchicalConfiguration metadataType : metadataList) {
            MetadataField type = new MetadataField(metadataType.getString("@field"), metadataType.getString("@metadata"),
                    metadataType.getString("@regularExpression", null), metadataType.getString("@docType", "volume"),
                    metadataType.getString("@validationExpression", null), metadataType.getString("@identifier", null));
            metadataFieldList.add(type);
        }

        List<HierarchicalConfiguration> personList = xmlConfig.configurationsAt("/person");
        for (HierarchicalConfiguration metadataType : personList) {
            PersonField type = new PersonField(metadataType.getString("@field"), metadataType.getString("@metadata"),
                    metadataType.getString("@firstname"), metadataType.getString("@lastname"), metadataType.getString("@docType", "volume"),
                    metadataType.getString("@regularExpression", null), metadataType.getString("@validationExpression", null),
                    metadataType.getString("@identifier", null));
            personFieldList.add(type);
        }

        username = xmlConfig.getString("/authentication/username", null);
        password = xmlConfig.getString("/authentication/password", null);
        loginUrl = xmlConfig.getString("/authentication/loginUrl", null);
        sessionid = xmlConfig.getString("/authentication/sessionid", null);
        headerParameter = xmlConfig.getString("/authentication/headerParameter", null);

    }

    @Data
    @AllArgsConstructor
    public class DocumentType {
        private String field;
        private String publicationType;
        private String publicationAnchorType;
    }

    @Data
    @AllArgsConstructor
    public class MetadataField {

        //        path to the field in json
        private String field;
        //        name of the field in ruleset
        private String metadata;
        //        regex to manipulate the value
        private String manipulateRegularExpression;
        //        volume or anchor
        private String docType;
        // check if regular expression matches with actual value
        private String validateRegularExpression;

        private String identifier;
    }

    @Data
    @AllArgsConstructor
    public class PersonField {
        //        path to the field in json
        private String field;
        //        name of the field in ruleset
        private String metadata;
        //        regex to get the firstname
        private String firstname;
        //        regex to get the lastname
        private String lastname;
        //        volume or anchor
        private String docType;
        //        regex to manipulate the value
        private String manipulateRegularExpression;
        // check if regular expression matches with actual value
        private String validateRegularExpression;

        private String identifier;

    }
}
