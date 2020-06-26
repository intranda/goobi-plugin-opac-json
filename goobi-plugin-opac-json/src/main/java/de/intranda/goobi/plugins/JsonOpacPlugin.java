package de.intranda.goobi.plugins;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.oro.text.perl.Perl5Util;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import de.intranda.goobi.plugins.util.Config;
import de.intranda.goobi.plugins.util.Config.DocumentType;
import de.intranda.goobi.plugins.util.Config.MetadataField;
import de.intranda.goobi.plugins.util.Config.PersonField;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.HttpClientHelper;
import de.sub.goobi.helper.UghHelper;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.minidev.json.JSONArray;
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
public class JsonOpacPlugin implements IOpacPlugin {

    /**
     * archiv: 304086 multivolume: 7748458 monograpgh: 9869346
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

    private int hitcount;
    protected String gattung = "Aa";
    protected String atstsl;
    protected ConfigOpacCatalogue coc;

    private String templateName;

    private String sessionId = null;

    public Config getConfig(String templateName) {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());
        xmlConfig.setListDelimiter(',');
        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[./template='" + templateName + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template='*']");
        }
        Config config = new Config(myconfig);

        return config;

    }

    @Override
    public Fileformat search(String inSuchfeld, String inSuchbegriff, ConfigOpacCatalogue coc, Prefs inPrefs) throws Exception {
        this.coc = coc;

        if (config == null) {
            config = getConfig(templateName);
        }
        Fileformat fileformat = null;
        String url = coc.getAddress() + inSuchbegriff.trim();
        String response = null;

        if (config.getLoginUrl() != null) {
            String loginUrl = config.getLoginUrl();
            loginUrl = loginUrl.replace("{username}", config.getUsername());
            loginUrl = loginUrl.replace("{password}", config.getPassword());
            String login = login(config.getPassword(), loginUrl);
            Object document = Configuration.defaultConfiguration().jsonProvider().parse(login);
            sessionId = JsonPath.read(document, config.getSessionid());
        }

        if (StringUtils.isNotBlank(config.getUsername()) && StringUtils.isNotBlank(config.getPassword()) && config.getLoginUrl() == null) {
            response = getStringFromUrl(url, config.getUsername(), config.getPassword(), config.getHeaderParameter(), sessionId);
        } else {
            response = getStringFromUrl(url, null, null, config.getHeaderParameter(), sessionId);
        }
        if (StringUtils.isNotBlank(response)) {
            hitcount = 1;
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

                parseMetadata(document, anchor, logical, inPrefs, config);
                parsePerson(document, anchor, logical, inPrefs, config);

                gattung = digDoc.getLogicalDocStruct().getType().getName();
                //                Metadata pathimagefiles = new Metadata(inPrefs.getMetadataTypeByName("pathimagefiles"));
                //                ConfigOpacDoctype codt = getOpacDocType();

            }

        } else {
            hitcount = 0;
        }
        return fileformat;
    }

    private void parsePerson(Object document, DocStruct anchor, DocStruct logical, Prefs inPrefs, Config config) {
        for (PersonField pf : config.getPersonFieldList()) {
            try {
                Object object = JsonPath.read(document, pf.getField());
                if (object instanceof List) {
                    List<?> valueList = (List) object;
                    for (Object value : valueList) {
                        String stringValue = getValueAsString(value);
                        String normdata = null;
                        if (pf.getIdentifier() != null) {
                            normdata = getValueAsString(JsonPath.read(document, pf.getIdentifier().replace("THIS", stringValue)));
                        }
                        addPerson(stringValue, normdata, pf, anchor, logical, inPrefs);
                    }
                } else {
                    String stringValue = getValueAsString(object);
                    String normdata = null;
                    if (pf.getIdentifier() != null) {
                        normdata = getValueAsString(JsonPath.read(document, pf.getIdentifier().replace("THIS", stringValue)));
                    }
                    addPerson(stringValue, normdata, pf, anchor, logical, inPrefs);
                }
            } catch (PathNotFoundException e) {
                log.info("Path is invalid or field could not be found ", e);
            }
        }
    }

    private void parseMetadata(Object document, DocStruct anchor, DocStruct logical, Prefs prefs, Config config) {
        for (MetadataField mf : config.getMetadataFieldList()) {
            try {
                Object object = JsonPath.read(document, mf.getField());
                if (object instanceof List) {
                    List<?> valueList = (List) object;
                    for (Object value : valueList) {
                        String stringValue = getValueAsString(value);
                        String normdata = null;
                        if (mf.getIdentifier() != null) {
                            normdata = getValueAsString(JsonPath.read(document, mf.getIdentifier().replace("THIS", stringValue)));
                        }
                        addMetadata(stringValue, normdata, mf, anchor, logical, prefs);
                    }
                } else {
                    String stringValue = getValueAsString(object);
                    String normdata = null;
                    if (mf.getIdentifier() != null) {
                        normdata = getValueAsString(JsonPath.read(document, mf.getIdentifier().replace("THIS", stringValue)));
                    }
                    addMetadata(stringValue, normdata, mf, anchor, logical, prefs);
                }
            } catch (PathNotFoundException e) {
                log.debug("Path is invalid or field could not be found ", e);
            }
        }
    }

    private void addPerson(String stringValue, String identifier, PersonField pf, DocStruct anchor, DocStruct logical, Prefs prefs) {
        if (StringUtils.isNotBlank(stringValue)) {
            if (StringUtils.isNotBlank(pf.getValidateRegularExpression())) {
                if (!perlUtil.match(pf.getValidateRegularExpression(), stringValue)) {
                    return;
                }
            }

            if (StringUtils.isNotBlank(pf.getManipulateRegularExpression())) {
                stringValue = perlUtil.substitute(pf.getManipulateRegularExpression(), stringValue);
            }
            if (pf.isFollowLink() && StringUtils.isNotBlank(pf.getTemplateName())) {
                Config templateConfig = getConfig(pf.getTemplateName());
                String uri = null;
                if (StringUtils.isNotBlank(pf.getBasisUrl())) {
                    uri = pf.getBasisUrl() + stringValue;
                } else {
                    uri = coc.getAddress() + stringValue;
                }
                String response = null;

                if (StringUtils.isNotBlank(config.getUsername()) && StringUtils.isNotBlank(config.getPassword()) && config.getLoginUrl() == null) {
                    response = getStringFromUrl(uri, config.getUsername(), config.getPassword(), config.getHeaderParameter(), sessionId);
                } else {
                    response = getStringFromUrl(uri, null, null, config.getHeaderParameter(), sessionId);
                }

                if (StringUtils.isNotBlank(response)) {
                    Object document = Configuration.defaultConfiguration().jsonProvider().parse(response);
                    parseMetadata(document, anchor, logical, prefs, templateConfig);
                    parsePerson(document, anchor, logical, prefs, templateConfig);
                }

            } else {
                String firstname = perlUtil.substitute(pf.getFirstname(), stringValue);
                String lastname = perlUtil.substitute(pf.getLastname(), stringValue);
                try {
                    Person person = new Person(prefs.getMetadataTypeByName(pf.getMetadata()));
                    person.setFirstname(firstname);
                    person.setLastname(lastname);
                    if (identifier != null) {
                        person.setAuthorityValue(identifier);
                    }
                    if (anchor != null && pf.getDocType().equals("anchor")) {
                        anchor.addPerson(person);
                    } else {
                        logical.addPerson(person);
                    }
                } catch (MetadataTypeNotAllowedException | IncompletePersonObjectException e) {
                    log.debug(e);
                }

            }}
    }

    private void addMetadata(String stringValue, String identifier, MetadataField mf, DocStruct anchor, DocStruct logical, Prefs prefs) {
        if (StringUtils.isNotBlank(stringValue)) {
            if (StringUtils.isNotBlank(mf.getValidateRegularExpression())) {
                if (!perlUtil.match(mf.getValidateRegularExpression(), stringValue)) {
                    return;
                }
            }

            if (StringUtils.isNotBlank(mf.getManipulateRegularExpression())) {
                stringValue = perlUtil.substitute(mf.getManipulateRegularExpression(), stringValue);
            }
            if (mf.isFollowLink() && StringUtils.isNotBlank(mf.getTemplateName())) {
                Config templateConfig = getConfig(mf.getTemplateName());
                String uri = null;
                if (StringUtils.isNotBlank(mf.getBasisUrl())) {
                    uri = mf.getBasisUrl() + stringValue;
                } else {
                    uri = coc.getAddress() + stringValue;
                }
                String response = null;


                if (StringUtils.isNotBlank(config.getUsername()) && StringUtils.isNotBlank(config.getPassword()) && config.getLoginUrl() == null) {
                    response = getStringFromUrl(uri, config.getUsername(), config.getPassword(), config.getHeaderParameter(), sessionId);
                } else {
                    response = getStringFromUrl(uri, null, null, config.getHeaderParameter(), sessionId);
                }

                if (StringUtils.isNotBlank(response)) {
                    Object document = Configuration.defaultConfiguration().jsonProvider().parse(response);
                    parseMetadata(document, anchor, logical, prefs, templateConfig);
                    parsePerson(document, anchor, logical, prefs, templateConfig);
                }

            } else {
                try {
                    Metadata metadata = new Metadata(prefs.getMetadataTypeByName(mf.getMetadata()));
                    metadata.setValue(stringValue);
                    if (identifier != null) {
                        metadata.setAuthorityValue(identifier);
                    }

                    if (anchor != null && mf.getDocType().equals("anchor")) {
                        anchor.addMetadata(metadata);
                    } else {
                        logical.addMetadata(metadata);
                    }
                } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                    log.debug(e);
                }
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
        } else if (value instanceof LinkedHashMap) {
            Map<String, String> map = (Map<String, String>) value;
            for (String key : map.keySet()) {
                log.error("not mapped: " + key + ": " + map.get(key));
            }
        }else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            return (String) array.get(0);
        }

        else {
            log.error("Type not mapped: " + value.getClass());
        }

        return anwer;
    }

    @Override
    public int getHitcount() {
        return hitcount;
    }

    @Override
    public ConfigOpacDoctype getOpacDocType() {

        ConfigOpac co;
        ConfigOpacDoctype cod = null;

        co = ConfigOpac.getInstance();
        cod = co.getDoctypeByMapping(this.gattung, this.coc.getTitle());
        if (cod == null) {
            cod = co.getAllDoctypes().get(0);
            this.gattung = cod.getMappings().get(0);
        }
        return cod;
    }

    @Override
    public String createAtstsl(String title, String author) {
        title = Normalizer.normalize(title, Normalizer.Form.NFC);
        if (author != null) {
            author = Normalizer.normalize(author, Normalizer.Form.NFC);
        }

        StringBuilder result = new StringBuilder(8);
        if (author != null && author.trim().length() > 0) {
            result.append(author.length() > 4 ? author.substring(0, 4) : author);
            result.append(title.length() > 4 ? title.substring(0, 4) : title);
        } else {
            StringTokenizer titleWords = new StringTokenizer(title);
            int wordNo = 1;
            while (titleWords.hasMoreTokens() && wordNo < 5) {
                String word = titleWords.nextToken();
                switch (wordNo) {
                    case 1:
                        result.append(word.length() > 4 ? word.substring(0, 4) : word);
                        break;
                    case 2:
                    case 3:
                        result.append(word.length() > 2 ? word.substring(0, 2) : word);
                        break;
                    case 4:
                        result.append(word.length() > 1 ? word.substring(0, 1) : word);
                        break;
                }
                wordNo++;
            }
        }
        String res = UghHelper.convertUmlaut(result.toString()).toLowerCase();
        return res.replaceAll("[\\W]", "");
    }

    @Override
    public String getAtstsl() {
        return this.atstsl;
    }

    @Override
    public void setAtstsl(String atstsl) {
        this.atstsl = atstsl;
    }

    @Override
    public String getGattung() {
        return gattung;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    private static String login(String password, String... parameter) {
        String response = "";
        CloseableHttpClient client = null;
        String url = parameter[0];
        HttpPost method = new HttpPost(url);
        if (parameter != null && parameter.length > 4) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(parameter[3], Integer.valueOf(parameter[4]).intValue()),
                    new UsernamePasswordCredentials(parameter[1], parameter[2]));
            client = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        } else {
            client = HttpClientBuilder.create().build();
        }
        try {
            if (StringUtils.isNotBlank(password)) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("password", password));
                method.setEntity(new UrlEncodedFormEntity(params));
            }
            response = client.execute(method, HttpClientHelper.stringResponseHandler);
        } catch (IOException e) {
            log.error("Cannot execute URL " + url, e);
        } finally {
            method.releaseConnection();

            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
        return response;
    }

    private static String getStringFromUrl(String url, String username, String password, String headerParam, String headerParamValue) {
        String response = "";
        CloseableHttpClient client = null;
        HttpGet method = new HttpGet(url);

        if (username != null && password != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(username, password));
            client = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        } else {
            client = HttpClientBuilder.create().build();
        }

        if (headerParam != null) {
            // add header parameter
            method.setHeader(headerParam, headerParamValue);
        }

        try {
            response = client.execute(method, HttpClientHelper.stringResponseHandler);
        } catch (IOException e) {
            log.error("Cannot execute URL " + url, e);
        } finally {
            method.releaseConnection();

            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
        return response;
    }
}
