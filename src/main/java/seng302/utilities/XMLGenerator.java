package seng302.utilities;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import seng302.gameServer.messages.XMLMessageSubType;
import seng302.model.stream.xml.generator.RaceXMLTemplate;
import seng302.model.stream.xml.generator.RegattaXMLTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Random;

/**
 * An XML generator to generate the Race, Boat, and Regatta XML dynamically
 */
public class XMLGenerator {
    private static final String XML_TEMPLATE_DIR = "/server_config/xml_templates";
    private static final String REGATTA_TEMPLATE_NAME = "regatta.ftlh";
    private static final String BOATS_TEMPLATE_NAME = "boats.ftlh";
    private static final String RACE_TEMPLATE_NAME = "race.ftlh";
    private Configuration configuration;
    private RegattaXMLTemplate regatta;
    private RaceXMLTemplate race;

    public static RegattaXMLTemplate DEFAULT_REGATTA = new RegattaXMLTemplate("Party Parrot Test Server " + new Random().nextInt(100),
            "Bermuda",
            57.6679590,
            11.8503233);

    /**
     * Set up a configuration instance for Apache Freemake
     */
    private void setupConfiguration() {
        configuration = new Configuration(Configuration.VERSION_2_3_26);

        try {
            configuration.setClassForTemplateLoading(getClass(), XML_TEMPLATE_DIR);
        } catch (NullPointerException e){
            System.out.println("[FATAL] Server could not load XML Template directory, ensure this directory isn't empty");
        }
    }

    /**
     * Create an instance of the XML Generator
     */
    public XMLGenerator() {
        setupConfiguration();
    }

    /**
     * Set the race regatta to send to players
     * Note: This must be set before a regatta message can be generated
     * @param regatta The race regatta
     */
    public void setRegattaTemplate(RegattaXMLTemplate regatta) {
        this.regatta = regatta;
    }

    /**
     * Set the race to send to players
     * Note: This must be set before a boat or race message can be generated
     * @param race The race
     */
    public void setRaceTemplate(RaceXMLTemplate race) {
        this.race = race;
    }

    /**
     * Parse an XML template and generate the output as a string
     * @param templateName The templates file name
     * @param type The XML message sub type
     */
    private String parseToXmlString(String templateName, XMLMessageSubType type) throws IOException, TemplateException {
        Template template;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(os);

        template = configuration.getTemplate(templateName);

        switch (type) {
            case REGATTA:
                template.process(regatta, writer);
                break;

            case BOAT:
                template.process(race, writer);
                break;

            case RACE:
                template.process(race, writer);
                break;

            default:
                throw new UnsupportedOperationException();
        }

        try {
            return os.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.out.println("[FATAL] UTF-8 Not supported");
            return null;
        }
    }

    /**
     * Get the race regatta as a string
     * Note: Regatta must be set before calling this
     * @return String containing the regatta XML, null if there was an error
     */
    public String getRegattaAsXml(){
        String result = null;

        if (regatta == null) regatta = DEFAULT_REGATTA;

        try {
            result = parseToXmlString(REGATTA_TEMPLATE_NAME, XMLMessageSubType.REGATTA);
        } catch (TemplateException e) {
            System.out.println("[FATAL] Error parsing regatta");
        } catch (IOException e) {
            System.out.println("[FATAL] Error reading regatta");
        }

        return result;
    }

    /**
     * Get the boats XML as a string
     * Note: Race must be set before calling this
     * @return String containing the boats XML, null if there was an error
     */
    public String getBoatsAsXml() {
        String result = null;

        if (race == null) return null;

        try {
            result = parseToXmlString(BOATS_TEMPLATE_NAME, XMLMessageSubType.BOAT);
        } catch (TemplateException e) {
            System.out.println("[FATAL] Error parsing boats");
        } catch (IOException e) {
            System.out.println("[FATAL] Error reading boats");
        }

        return result;
    }

    /**
     * Get the race XML as a string
     * Note: Race must be set before calling this
     * @return String containing the race XML, null if there was an error
     */
    public String getRaceAsXml() {
        String result = null;

        if (race == null) return null;

        try {
            result = parseToXmlString(RACE_TEMPLATE_NAME, XMLMessageSubType.RACE);
        } catch (TemplateException e) {
            System.out.println("[FATAL] Error parsing race");
        } catch (IOException e) {
            System.out.println("[FATAL] Error reading race");
        }

        return result;
    }

    public static void setDefaultRaceName(String raceName){
        DEFAULT_REGATTA.setRegattaName(raceName);
    }

    public static void setDefaultMapName(String mapName){
        DEFAULT_REGATTA.setCourseName(mapName);
    }

    public RegattaXMLTemplate getRegatta() {
        return regatta;
    }

    public RaceXMLTemplate getRace() {
        return race;
    }
}