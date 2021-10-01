package de.intranda.goobi.plugins.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.interfaces.ISearchField;

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

    private List<ISearchField> fieldList = new ArrayList<>();

    private boolean showResultList = false;
    private String additionalApiUrl;

    /**
     * loads the &lt;config&gt; block from xml file
     * 
     * @param xmlConfig
     */

    public Config(SubnodeConfiguration xmlConfig) {

        defaultPublicationType = xmlConfig.getString("/defaultPublicationType", null);
        showResultList = xmlConfig.getBoolean("/showResultList", false);
        additionalApiUrl =  xmlConfig.getString("/urlForSecondCall", null);
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
                    metadataType.getString("@validationExpression", null), metadataType.getString("@identifier", null),
                    metadataType.getBoolean("@followLink", false), metadataType.getString("@templateName", null),
                    metadataType.getString("@basisUrl", null));
            metadataFieldList.add(type);
        }

        List<HierarchicalConfiguration> personList = xmlConfig.configurationsAt("/person");
        for (HierarchicalConfiguration metadataType : personList) {
            PersonField type = new PersonField(metadataType.getString("@field"), metadataType.getString("@metadata"),
                    metadataType.getString("@firstname"), metadataType.getString("@lastname"), metadataType.getString("@docType", "volume"),
                    metadataType.getString("@regularExpression", null), metadataType.getString("@validationExpression", null),
                    metadataType.getString("@identifier", null), metadataType.getBoolean("@followLink", false),
                    metadataType.getString("@templateName", null), metadataType.getString("@basisUrl", null));
            personFieldList.add(type);
        }

        username = xmlConfig.getString("/authentication/username", null);
        password = xmlConfig.getString("/authentication/password", null);
        loginUrl = xmlConfig.getString("/authentication/loginUrl", null);
        sessionid = xmlConfig.getString("/authentication/sessionid", null);
        headerParameter = xmlConfig.getString("/authentication/headerParameter", null);

        List<HierarchicalConfiguration> fields = xmlConfig.configurationsAt("/field");
        for (HierarchicalConfiguration field : fields) {

            SearchField searchField = new SearchField();

            searchField.setId(field.getString("@id"));

            searchField.setLabel(field.getString("/label", null));

            searchField.setType(field.getString("/type", "text"));

            String[] array = field.getStringArray("/select");
            if (array.length > 0) {
                List<String> selection = Arrays.asList(array);
                searchField.setSelectList(selection);
                searchField.setSelectedField(selection.get(0));
            }
            searchField.setText(field.getString("/defaultText", ""));

            searchField.setUrl(field.getString("/url", null));

            fieldList.add(searchField);
        }

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
        // search for identifier value
        private String identifier;
        // follow the link in metadata value
        private boolean followLink;
        // use this template for metadata linked metadata
        private String templateName;
        // basis url for new request
        private String basisUrl;

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
        // search for identifier value
        private String identifier;
        // follow the link in metadata value
        private boolean followLink;
        // use this template for metadata linked metadata
        private String templateName;
        // basis url for new request
        private String basisUrl;

    }

    @Data
    public class SearchField implements ISearchField {

        private String id;

        // displayed label
        private String label;

        // type of the field, implemented are text, select and select+text
        private String type;

        // list of possible values
        private List<String> selectList;

        // value of the selected field
        private String selectedField;

        // entered text, gets filled with default value
        private String text;

        private String url;
    }
}
