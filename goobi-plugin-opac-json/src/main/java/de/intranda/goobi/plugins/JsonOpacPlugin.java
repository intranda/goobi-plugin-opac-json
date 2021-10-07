package de.intranda.goobi.plugins;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
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
import org.goobi.interfaces.IJsonPlugin;
import org.goobi.interfaces.ISearchField;
import org.goobi.production.enums.PluginType;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import lombok.Setter;
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
public class JsonOpacPlugin implements IJsonPlugin {

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

    @Setter
    private String opacName;

    private String sessionId = null;

    @Setter
    @Getter
    private String workflowTitle;

    @Getter
    private List<Map<String, String>> overviewList;

    @Getter
    @Setter
    private boolean showModal = false;

    @Getter
    @Setter
    private String selectedUrl;

    @Setter
    private boolean testMode = true;

    public Config getConfig(String templateName) {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[./template='" + templateName + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template='*']");
        }
        Config config = new Config(myconfig);
        return config;
    }

    public Config getConfigForOpac() {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[workflow='" + workflowTitle + "'][@name='" + opacName + "']");
        } catch (IllegalArgumentException e) {
            try {
                myconfig = xmlConfig.configurationAt("//config[workflow='" + workflowTitle + "'][not(@name)]");
            } catch (IllegalArgumentException e1) {
                try {
                    myconfig = xmlConfig.configurationAt("//config[@name='" + opacName + "']");
                } catch (IllegalArgumentException e2) {
                    myconfig = xmlConfig.configurationAt("//config[not(workflow)][not(@name)]");
                }
            }
        }

        config = new Config(myconfig);

        return config;
    }

    @Override
    public Fileformat search(String inSuchfeld, String inSuchbegriff, ConfigOpacCatalogue coc, Prefs inPrefs) throws Exception {
        this.coc = coc;

        if (config == null) {
            config = getConfigForOpac();
        }
        boolean prepareModal = false;
        Fileformat fileformat = null;
        String response = null;

        if (testMode) { // create test string
            if (StringUtils.isNotBlank(selectedUrl)) {
                response = "{\"jsonModelType\":\"BibSolrRecord\",\"source\":\"ils\",\"recordType\":\"bib\",\"uri\":\"/ils/item/4168081?bib=2716227\",\"callNumber\":\"Hf75 3 19-20\",\"callNumberWithPublicNote\":\"Hf75 3 19-20 Bound with other titles. To view other titles search by call number: Hf75 3 19-20.\",\"creator\":[\"Sauvage, T. (Thomas)\"],\"title\":[\"Un vaudevilliste : comédie en un acte, en prose\"],\"creationPlace\":[\"Paris? : (Paris\"],\"publisher\":[\"s.n., Imprimerie de Mme Ve Dondey-Dupré).\"],\"date\":[\"1839?]\"],\"extent\":[\"18, [2] p. (last 2 p. blank) : 25 cm.\"],\"material\":[\"ill. ;\"],\"language\":[\"French\"],\"languageCode\":[\"fre\"],\"description\":[\"Caption title.\",\"Printer's name from colophon.\",\"\\\"Représentée, pour la première fois, à Paris, sur le Théâtre de la Renaissance, le 6 juillet 1839.\\\" -- p. [1].\",\"At head of title, an illustration (drawing) from scene 22.\",\"Cast list on p. [1].\",\"Printed in double columns.\",\"SML,Y Hf75 3 20: In Bibl. dram., 3e sér., t. 20.\"],\"subjectTopic\":[\"French drama (Comedy)\",\"Authors, French\"],\"genre\":[\"Comedies\",\"One-act plays\"],\"orbisBibId\":\"2716227\",\"orbisBarcode\":\"39002001054627\",\"dateStructured\":[\"1839\"],\"illustrativeMatter\":\"    \",\"subjectEra\":\"19th century. 19th century.\",\"titleStatement\":[\"Un vaudevilliste : comédie en un acte, en prose / Par MM. T. Sauvage et Maurice Saint-Aguet\"],\"contributor\":[\"Dondey-Dupré Mme Ve.\",\"Théâtre de la Renaissance (Paris, France).\"],\"repository\":\"Sterling Memorial Library\",\"relatedTitleDisplay\":[\"Dondey-Dupré Mme Ve.\",\"Théâtre de la Renaissance (Paris, France).\"],\"creatorDisplay\":[\"Sauvage, T. (Thomas)\"],\"contributorDisplay\":[\"Dondey-Dupré Mme Ve.\",\"Théâtre de la Renaissance (Paris, France).\"],\"volumeEnumeration\":\"19-20\",\"dependentUris\":[\"/ils/holding/3055899\",\"/ils/item/4168081\",\"/ils/bib/2716227\",\"/ils/barcode/39002001054627\"],\"bibId\":2716227,\"suppressInOpac\":false,\"createDate\":\"2002-06-01T04:00:00.000+0000\",\"updateDate\":\"2011-03-10T18:55:15.000+0000\",\"holdingId\":3055899,\"itemId\":4168081,\"children\":[]}";
                selectedUrl = null;
            } else {
                response = getTestString();
                prepareModal = true;
            }
        } else

            if (StringUtils.isNotBlank(selectedUrl) && config.isShowResultList()) {
                String url = config.getAdditionalApiUrl() + selectedUrl;
                response = search(url);
                selectedUrl = null;
            } else {
                String url = prepareSearchUrl(inSuchfeld, inSuchbegriff);
                response = search(url);
                prepareModal = true;
            }
        if (StringUtils.isNotBlank(response)) {
            hitcount = 1;
            if (config.isShowResultList() && prepareModal) {
                // prepare overview list
                Gson gson = new Gson();
                Type typeOf = new TypeToken<List<Map<String, String>>>() {
                }.getType();
                overviewList = gson.fromJson(response, typeOf);

                hitcount = 1;
                showModal = true;
                return null;
                //                if (overviewList.size()> 1) {
                //                    showModal = true;
                //                    return null;
                //                } else {
                //                    // if only one record is available: don't show modal and continue
                //                    String url = config.getAdditionalApiUrl() + overviewList.get(0).getUri();
                //                    response = search(url);
                //                }
            }
            showModal = false;
            Object document = Configuration.defaultConfiguration().jsonProvider().parse(response);
            String publicationType = null;
            String anchorType = null;
            for (DocumentType documentType : config.getDocumentTypeList()) {
                try {
                    Object object = JsonPath.read(document, documentType.getField());
                    if (object instanceof List) {
                        List<?> valueList = (List<?>) object;
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
                Metadata imageFiles = new Metadata(inPrefs.getMetadataTypeByName("pathimagefiles"));
                imageFiles.setValue("/images");
                physical.addMetadata(imageFiles);
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

    private String prepareSearchUrl(String inSuchfeld, String inSuchbegriff) {
        String url = null;
        // find url to use
        for (ISearchField sf : config.getFieldList()) {
            switch (sf.getType()) {
                case "text":
                    if (StringUtils.isNotBlank(sf.getText())) {
                        url = sf.getUrl();
                    }
                    break;
                case "select":
                    if (StringUtils.isNotBlank(sf.getSelectedField())) {
                        url = sf.getUrl();
                    }
                    break;
                case "select+text":
                    if (StringUtils.isNotBlank(sf.getText()) && StringUtils.isNotBlank(sf.getSelectedField())) {
                        url = sf.getUrl();
                    }
                    break;
            }
        }
        // plugin is called from another plugin
        if (StringUtils.isBlank(url) && StringUtils.isNotBlank(inSuchfeld)) {
            for (ISearchField sf : config.getFieldList()) {
                if (sf.getId().equals(inSuchfeld)) {
                    url = sf.getUrl().replace("{" + sf.getId() + ".text}", inSuchbegriff);
                }
            }

        } else if (StringUtils.isBlank(url)) {
            url = coc.getAddress() + inSuchbegriff;
        }
        // replace variables in url
        for (ISearchField sf : config.getFieldList()) {
            switch (sf.getType()) {
                case "text":
                    if (StringUtils.isNotBlank(sf.getText())) {
                        url = url.replace("{" + sf.getId() + ".text}", sf.getText());
                    }
                    break;
                case "select":
                    if (StringUtils.isNotBlank(sf.getSelectedField())) {
                        url = url.replace("{" + sf.getId() + ".select}", sf.getSelectedField());
                    }
                    break;
                case "select+text":
                    if (StringUtils.isNotBlank(sf.getText())) {
                        url = url.replace("{" + sf.getId() + ".text}", sf.getText());
                    }
                    if (StringUtils.isNotBlank(sf.getSelectedField())) {
                        url = url.replace("{" + sf.getId() + ".select}", sf.getSelectedField());
                    }

            }
        }
        return url;
    }

    private String search(String url) {
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
        return response;
    }

    private void parsePerson(Object document, DocStruct anchor, DocStruct logical, Prefs inPrefs, Config config) {
        for (PersonField pf : config.getPersonFieldList()) {
            try {
                Object object = JsonPath.read(document, pf.getField());
                if (object instanceof List) {
                    List<?> valueList = (List<?>) object;
                    for (Object value : valueList) {
                        if (value != null) {
                            String stringValue = getValueAsString(value);
                            String normdata = null;
                            if (pf.getIdentifier() != null) {
                                normdata = getValueAsString(JsonPath.read(document,
                                        pf.getIdentifier().replace("THIS", stringValue.replace("\'", "\\\'").replace("\"", "\\\""))));
                            }
                            addPerson(stringValue, normdata, pf, anchor, logical, inPrefs);
                        }
                    }
                } else {
                    if (object != null) {
                        String stringValue = getValueAsString(object);
                        String normdata = null;
                        if (pf.getIdentifier() != null) {
                            normdata = getValueAsString(JsonPath.read(document,
                                    pf.getIdentifier().replace("THIS", stringValue.replace("\'", "\\\'").replace("\"", "\\\""))));
                        }
                        addPerson(stringValue, normdata, pf, anchor, logical, inPrefs);
                    }
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
                if (object != null) {
                    if (object instanceof List) {
                        List<?> valueList = (List<?>) object;
                        for (Object value : valueList) {
                            if (value != null) {
                                String stringValue = getValueAsString(value);
                                String normdata = null;
                                if (mf.getIdentifier() != null) {
                                    normdata = getValueAsString(JsonPath.read(document,
                                            mf.getIdentifier().replace("THIS", stringValue.replace("\'", "\\\'").replace("\"", "\\\""))));
                                }
                                addMetadata(stringValue, normdata, mf, anchor, logical, prefs);
                            }
                        }
                    } else {
                        if (object != null) {
                            String stringValue = getValueAsString(object);
                            String normdata = null;
                            if (mf.getIdentifier() != null) {
                                normdata = getValueAsString(JsonPath.read(document,
                                        mf.getIdentifier().replace("THIS", stringValue.replace("\'", "\\\'").replace("\"", "\\\""))));
                            }
                            addMetadata(stringValue, normdata, mf, anchor, logical, prefs);
                        }
                    }
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

            }
        }
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
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) value;
            for (String key : map.keySet()) {
                log.error("not mapped: " + key + ": " + map.get(key));
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (!array.isEmpty()) {
                return (String) array.get(0);
            }
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

    static String getStringFromUrl(String url, String username, String password, String headerParam, String headerParamValue) {
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

    @Override
    public String getGui() {
        return "/uii/jsonOpacPlugin.xhtml";
    }

    @Override
    public List<ISearchField> getSearchFieldList() {
        if (config == null) {
            config = getConfigForOpac();
        }
        return config.getFieldList();
    }

    public int getSizeOfSearchFieldList() {
        return getSearchFieldList().size();
    }

    @Override
    public List<ConfigOpacCatalogue> getOpacConfiguration(String workflowName, String title) {
        List<ConfigOpacCatalogue> answer = new ArrayList<>();
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(getTitle());
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        String xpath = "";
        String[] names = null;

        if (StringUtils.isNotBlank(workflowName)) {
            xpath = "/config[workflow='" + workflowName + "']/@name";
            names = xmlConfig.getStringArray(xpath);
            if (names == null || names.length == 0) {
                xpath = "/config[not(workflow)]/@name";
                names = xmlConfig.getStringArray(xpath);
            }
        } else {
            // no workflowName is set, use all
            xpath = "/config/@name";
            names = xmlConfig.getStringArray(xpath);
        }

        ConfigOpacCatalogue coc = ConfigOpac.getInstance().getCatalogueByName(title);
        coc.setOpacPlugin(this);
        if (names == null || names.length == 0) {
            answer.add(coc);
        } else {
            for (String catalogueName : names) {
                coc = ConfigOpac.getInstance().getCatalogueByName(title);
                coc.setTitle(catalogueName);
                JsonOpacPlugin plugin = new JsonOpacPlugin();
                coc.setOpacPlugin(plugin);
                plugin.setOpacName(catalogueName);
                plugin.setWorkflowTitle(workflowName);
                answer.add(coc);
            }
        }
        return answer;
    }

    private String getTestString() {
        return "[\n"
                + "    {\"title\": \"Marcheￌﾁ de Saint-Pierre : meￌﾁlodrame en cinq actes / par Benjamin Antier et Alexis de Comberousse. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1848655\",\"bibId\": \"1848655\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Antier, Benjamin, 1787-1870. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Simplette la chevrieￌﾀre; vaudeville en un acte ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2083565\",\"bibId\": \"2083565\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Cogniard, Theￌﾁodore, 1806-1872. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Balochard, ou, Samedi, dimanche et lundi : vaudeville en trois actes / par MM. Depeuty et E. Vanderbuch. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2157244\",\"bibId\": \"2157244\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Dupeuty, M. (Charles), 1798-1865. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Bathilde; drame en trois actes ... Repreￌﾁsenteￌﾁ, pour la premieￌﾀre fois, aￌﾀ Paris, sur le Theￌﾁaￌﾂtre de la renaissance (Salle Ventadour) le 14 janvier 1839. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2509110\",\"bibId\": \"2509110\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Maquet, Auguste, 1813-1888. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1843\"\n"
                + "    },\n"
                + "    {\"title\": \"Maiￌﾂtresse et la fianceￌﾁe; drame en deux actes, meￌﾂleￌﾁ de chants, ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2785687\",\"bibId\": \"2785687\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Souvestre, Eￌﾁmile. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Jeunesse de Goethe, comeￌﾁdie en un acte, en vers, par Madame Louise Colet-Revoil. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2075761\",\"bibId\": \"2075761\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Colet, Louise, 1810-1876. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Faut que jeunesse se passe; comedie en trois actes, en prose, par m. de Rougemont, repreￌﾁsenteￌﾁ pour la premieￌﾀre fois sur le Theￌﾁatre-Francais, le ler juillet 1839. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2715653\",\"bibId\": \"2715653\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Rougemont, M. de (Michel-Nicholas Balisson), 1781-1840. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Trois bals : vaudeville en trois actes / par M. Bayard. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1963934\",\"bibId\": \"1963934\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Bayard, Jean-Francￌﾧois-Alfred, 1796-1853. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Rigobert; ou, Fais-mois rire; comeￌﾁdie-drame, en trois actes ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2129285\",\"bibId\": \"2129285\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Deligny, Eugeￌﾀne, 1816-1881. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Maurice, comeￌﾁdie-vaudeville en deux actes, par MM. Meￌﾁlesville [pseud.] et C. Duveyrier. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2158669\",\"bibId\": \"2158669\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Meￌﾁlesville, M., 1787-1865. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Manoir de Montlouvier : drame en cinq actes, en prose / par M. Rosier. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2690141\",\"bibId\": \"2690141\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Rosier, Joseph-Bernard, 1804-1880. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Diane de Chivri : drame en cinq actes / par M. Freￌﾁdeￌﾁric Soulieￌﾁ. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2772587\",\"bibId\": \"2772587\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Soulieￌﾁ, Freￌﾁdeￌﾁric, 1800-1847. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Fils de la folle : drame en cinq actes / par M. Freￌﾁdeￌﾁric Soulieￌﾁ. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2772588\",\"bibId\": \"2772588\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Soulieￌﾁ, Freￌﾁdeￌﾁric, 1800-1847. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Pascal et Chambord : comeￌﾁdie en deux actes, meￌﾂleￌﾁe de chant / par MM. Anicet Bourgeois et Edouard Brisebarre. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1862527\",\"bibId\": \"1862527\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Anicet-Bourgeois, M. (Auguste), 1806-1871. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Dieu vous beￌﾁnisse! : comeￌﾁdie-vaudeville en un acte / par MM. Ancelot et Paul Duport. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1862546\",\"bibId\": \"1862546\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Ancelot, Jacques-Arseￌﾀne-Francￌﾧois-Polycarpe, 1794-1854. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Lekain aￌﾀ Draguignan, comeￌﾁdie en deux actes, meￌﾂleￌﾁe de chant, par MM. De Forges et Paul Vermond, repreￌﾁseneￌﾁe pour la premieￌﾀre fois, aￌﾀ Paris, sur le Theￌﾁaￌﾂtre du Palais-Royal, le 23 janvier 1839. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2126526\",\"bibId\": \"2126526\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Forges, A. de (Auguste), 1805-1881. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1843\"\n"
                + "    },\n"
                + "    {\"title\": \"Marguerite d'Yorck; meￌﾁlodrame historique en trois actes, avec un prologue, ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2198884\",\"bibId\": \"2198884\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Fournier, N. (Narcisse), 1803-1880. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Amandine : vaudeville en deux actes / par MM. de Rougemont et A. Monnier. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2711689\",\"bibId\": \"2711689\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Balisson de Rougemont, Michel-Nicolas, 1781-1840. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Alchimiste : drame en cinq actes, en vers / par Alexandre Dumas. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2153993\",\"bibId\": \"2153993\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Dumas, Alexandre, 1802-1870. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1843\"\n"
                + "    },\n"
                + "    {\"title\": \"Plastron : comeￌﾁdie en deux actes, meￌﾂleￌﾁe du chant : repreￌﾁsenteￌﾁe, pour la premieￌﾀre fois, aￌﾀ Paris, sur le theￌﾁaￌﾂtre du Vaudeville, le 27 avril 1839 / par MM. Xavier, Duvert et Lauzanne. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2682778\",\"bibId\": \"2682778\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Xavier, M., 1798-1865. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Eￌﾁmile; ou, Six teￌﾂtes dans un chapeau; comeￌﾁdie-vaudeville en un acte, ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1965133\",\"bibId\": \"1965133\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Bayard, Jean-Francￌﾧois-Alfred, 1796-1853. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Mademoiselle de Belle-Isle; drame en cinq actes, en prose ... Repreￌﾁsenteￌﾁ pour la premieￌﾀre fois, aￌﾀ Paris, sur le Theￌﾁaￌﾂtre Francￌﾧais, le 2 avril 1839. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2162369\",\"bibId\": \"2162369\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Dumas, Alexandre, 1802-1870. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1843\"\n"
                + "    },\n"
                + "    {\"title\": \"Maria, drame en deux actes, meￌﾂleￌﾁ de chant, par MM. Paul Foucher et Laurencin. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2189911\",\"bibId\": \"2189911\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Foucher, Paul. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Deux jeunes femmes : drame en cinq actes et en prose / par V. de Saint-Hilaire ; repreￌﾁsenteￌﾁ, pour la premieￌﾀre fois, aￌﾀ Paris, sur le theￌﾁaￌﾂtre de la Renaissance, le 7 juin 1839. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2668718\",\"bibId\": \"2668718\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Saint-Hilaire, Amable Vilain de, b. 1795. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Gabrielle, ou, Les aides-de-camp : comeￌﾁdie-vaudeville en deux actes / par MM. Ancelot et Paul Duport. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=1862545\",\"bibId\": \"1862545\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Ancelot, Jacques-Arseￌﾀne-Francￌﾧois-Polycarpe, 1794-1854. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Naufrage de la Meￌﾁduse; drame en cinq actes ... \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2127636\",\"bibId\": \"2127636\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Desnoyer, Charles, 1806-1858. \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    },\n"
                + "    {\"title\": \"Vaudevilliste : comeￌﾁdie en un acte, en prose / Par MM. T. Sauvage et Maurice Saint-Aguet. \",\"recordType\": \"ils\",\"barcode\": \"39002001054627\",\"uri\": \"/ils/item/4168081?bib=2716227\",\"bibId\": \"2716227\",\"itemId\": \"4168081\",\"holdingId\": \"3055899\",\"author\": \"Sauvage, T. (Thomas) \",\"callNumber\": \"Hf75 3 19-20\",\"volume\": \"19-20\",\"pubDate\": \"1839\"\n"
                + "    }\n" + "]";
    }

}
