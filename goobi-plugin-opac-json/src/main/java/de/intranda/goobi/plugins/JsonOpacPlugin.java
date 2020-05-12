package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.apache.oro.text.perl.Perl5Util;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPluginVersion2;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import de.intranda.goobi.plugins.util.Config;
import de.intranda.goobi.plugins.util.Config.DocumentType;
import de.intranda.goobi.plugins.util.Config.MetadataField;
import de.intranda.goobi.plugins.util.Config.PersonField;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.HttpClientHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.IncompletePersonObjectException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class JsonOpacPlugin implements IOpacPluginVersion2 {

    /**
     * record: 304086
     * 
     * <catalogue title="json opac test">
     * <config description="json" address="https://files.intranda.com/" port="80" database="1.65" iktlist="IKTLIST-GBV.xml" ucnf="XPNOFF=1" opacType=
     * "intranda_opac_json" /> </catalogue>
     * 
     */

    private Perl5Util perlUtil = new Perl5Util();

    @Getter
    private String title = "intranda_opac_json";

    @Getter
    private PluginType type = PluginType.Opac;

    private Config config = null;

    public Config getConfig() {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config");
        } catch (IllegalArgumentException e) {
        }
        Config config = new Config(myconfig);

        return config;

    }

    @Override
    public Fileformat search(String inSuchfeld, String inSuchbegriff, ConfigOpacCatalogue coc, Prefs inPrefs) throws Exception {
        if (config == null) {
            config = getConfig();
        }
        Fileformat fileformat = null;
        String url = coc.getAddress() + inSuchbegriff;
        String response = HttpClientHelper.getStringFromUrl(url);

        if (StringUtils.isNotBlank(response)) {

            Object document = Configuration.defaultConfiguration().jsonProvider().parse(response);

            String publicationType = null;
            String anchorType = null;
            for (DocumentType documentType : config.getDocumentTypeList()) {
                try {
                    Object object = JsonPath.read(document, documentType.getField());
                    if (object instanceof List) {
                        List<?> valueList = (List) object;
                        if (!valueList.isEmpty()) {
                            publicationType = documentType.getPublicationType();
                            anchorType = documentType.getPublicationAnchorType();
                            break;
                        }
                    }
                } catch (PathNotFoundException e) {
                    log.info("Path is invalid or field could not be found ", e);
                }
            }

            if (publicationType == null && StringUtils.isNotBlank(config.getDefaultPublicationType())) {
                publicationType = config.getDefaultPublicationType();
            }

            if (publicationType == null) {
                log.info("Can't detect publication type for record " + inSuchbegriff);
                return null;
            }
            if (publicationType != null) {
                fileformat = new MetsMods(inPrefs);
                DigitalDocument digDoc = new DigitalDocument();
                fileformat.setDigitalDocument(digDoc);
                DocStruct logical = digDoc.createDocStruct(inPrefs.getDocStrctTypeByName(publicationType));
                DocStruct physical = digDoc.createDocStruct(inPrefs.getDocStrctTypeByName("BoundBook"));
                DocStruct anchor = null;
                if (StringUtils.isNotBlank(anchorType)) {
                    anchor = digDoc.createDocStruct(inPrefs.getDocStrctTypeByName(anchorType));
                    anchor.addChild(logical);
                    digDoc.setLogicalDocStruct(anchor);
                } else {
                    digDoc.setLogicalDocStruct(logical);
                }
                digDoc.setPhysicalDocStruct(physical);

                // pathimagefiles

                parseMetadata(document, anchor, logical, inPrefs);
                parsePerson(document, anchor, logical, inPrefs);

                Metadata pathimagefiles = new Metadata(inPrefs.getMetadataTypeByName("pathimagefiles"));

            }

        }
        return fileformat;
    }

    private void parsePerson(Object document, DocStruct anchor, DocStruct logical, Prefs inPrefs) {
        for (PersonField pf : config.getPersonFieldList()) {
            try {
                Object object = JsonPath.read(document, pf.getField());
                if (object instanceof List) {
                    List<?> valueList = (List) object;
                    for (Object value : valueList) {
                        String stringValue = getValueAsString(value);
                        addPerson(stringValue, pf, anchor, logical, inPrefs);
                    }
                } else {
                    String stringValue = getValueAsString(object);
                    addPerson(stringValue, pf, anchor, logical, inPrefs);
                }
            } catch (PathNotFoundException e) {
                log.info("Path is invalid or field could not be found ", e);
            }
        }
    }

    private void parseMetadata(Object document, DocStruct anchor, DocStruct logical, Prefs prefs) {
        for (MetadataField mf : config.getMetadataFieldList()) {
            try {
                Object object = JsonPath.read(document, mf.getField());
                if (object instanceof List) {
                    List<?> valueList = (List) object;
                    for (Object value : valueList) {
                        String stringValue = getValueAsString(value);
                        addMetadata(stringValue, mf, anchor, logical, prefs);
                    }
                } else {
                    String stringValue = getValueAsString(object);
                    addMetadata(stringValue, mf, anchor, logical, prefs);
                }
            } catch (PathNotFoundException e) {
                log.info("Path is invalid or field could not be found ", e);
            }
        }
    }

    private void addPerson(String stringValue, PersonField pf, DocStruct anchor, DocStruct logical, Prefs prefs) {
        if (StringUtils.isNotBlank(stringValue)) {
            // TODO first check if it matches?
            String firstname = perlUtil.substitute(pf.getFirstname(), stringValue);
            String lastname = perlUtil.substitute(pf.getLastname(), stringValue);
            try {
                Person person = new Person(prefs.getMetadataTypeByName(pf.getMetadata()));
                person.setFirstname(firstname);
                person.setLastname(lastname);
                if (anchor != null && pf.getDocType().equals("anchor")) {
                    anchor.addPerson(person);
                } else {
                    logical.addPerson(person);
                }
            } catch (MetadataTypeNotAllowedException | IncompletePersonObjectException e) {
                log.error(e);
            }
        }
    }

    private void addMetadata(String stringValue, MetadataField mf, DocStruct anchor, DocStruct logical, Prefs prefs) {
        if (StringUtils.isNotBlank(stringValue)) {
            if (StringUtils.isNotBlank(mf.getRegularExpression())) {
                stringValue = perlUtil.substitute(mf.getRegularExpression(), stringValue);
            }
            try {
                Metadata metadata = new Metadata(prefs.getMetadataTypeByName(mf.getMetadata()));
                metadata.setValue(stringValue);
                if (anchor != null && mf.getDocType().equals("anchor")) {
                    anchor.addMetadata(metadata);
                } else {
                    logical.addMetadata(metadata);
                }
            } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                log.error(e);
            }
        }
    }

    private String getValueAsString(Object value) {
        String anwer = null;
        if (value instanceof String) {
            anwer = (String) value;
        } else if (value instanceof Integer) {
            anwer = ((Integer) value).toString();
        } else if (value instanceof Boolean) {
            return (boolean) value ? "true" : "false";
        } else {
            log.error("Type not mapped: " + value.getClass());
        }

        return anwer;
    }

    @Override
    public int getHitcount() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getAtstsl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConfigOpacDoctype getOpacDocType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String createAtstsl(String value, String value2) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setAtstsl(String createAtstsl) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getGattung() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> getRawDataAsString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Path> getRecordPathList() {
        // TODO Auto-generated method stub
        return null;
    }

}
