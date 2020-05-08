package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPluginVersion2;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import de.intranda.goobi.plugins.util.Config;
import de.intranda.goobi.plugins.util.Config.DocumentType;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.HttpClientHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@PluginImplementation
@Log4j2
public class JsonOpacPlugin implements IOpacPluginVersion2 {

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

        JSONParser parser = new JSONParser();

        String url = coc.getAddress() + inSuchbegriff;
        String response = HttpClientHelper.getStringFromUrl(url);

        if (StringUtils.isNotBlank(response)) {

            try {
                JSONObject object = (JSONObject) parser.parse(response);

                detectDocumentType(object);

            } catch (Exception e) {
                log.error(e);
            }

        }
        // TODO Auto-generated method stub
        return null;
    }

    private void detectDocumentType(JSONObject object) {
        String publicationType = config.getDefaultPublicationType();

        publicationFound: for (DocumentType documentType : config.getDocumentTypeList()) {
            //
            String[] jsonPath = documentType.getJsonFieldName().split("\\.");
            Object o = object;
            for (String path : jsonPath) {
                if (o == null) {
                    continue;
                }
                if (o instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) o;
                    o = jsonObject.get(path);
                } else if (o instanceof JSONArray) {
                    JSONArray array = (JSONArray) o;

                    Iterator<JSONObject> iterator = array.iterator();
                    while (iterator.hasNext()) {
                        JSONObject key = iterator.next();
                        o = key.get(path);
                    }
                } else {
                    log.info("Unexpected json property type: " + o.getClass());
                }
            }
            if (o != null) {
                String value = o.toString();
                switch (documentType.getMatchType()) {
                    case "any":
                        publicationType = documentType.getPublicationType();
                        break publicationFound;
                    case "exact":
                        if (value.equals(documentType.getFieldValue())) {
                            publicationType = documentType.getPublicationType();
                        }
                        break publicationFound;
                    case "regex":
                        if (value.matches(documentType.getFieldValue())) {
                            publicationType = documentType.getPublicationType();
                        }
                        break publicationFound;
                    default:
                        log.error("Unsupported document match type");
                        break publicationFound;
                }
            }

        }

        System.out.println(publicationType);
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
