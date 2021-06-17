package org.xmlresolver.example;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlresolver.Resolver;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.cache.ResourceCache;
import org.xmlresolver.sources.ResolverSAXSource;
import org.xmlresolver.utils.URIUtils;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SampleApp {
    private static final ArrayList<String> catalogs = new ArrayList<>();
    private static final ArrayList<String> xsds = new ArrayList<>();
    private static Boolean useResolver = null;
    private static Boolean classpathCatalogs = null;
    private static boolean useCache = false;
    private static String cache = null;
    private static boolean dtdValidate = false;
    private static boolean validateCatalogs = false;
    private static String rng = null;
    private static String xsl = null;
    private static String document = null;

    public static void main(String[] args) {
        int pos = 0;

        if (args.length == 0) {
            usage();
        }

        while (pos < args.length) {
            String arg = args[pos];
            if (arg.startsWith("-")) {
                if ("-resolver".equals(arg)) {
                    if (useResolver != null) {
                        fail("You can only specify one of -resolver and -no-resolver");
                    }
                    useResolver = Boolean.TRUE;
                } else if ("-no-resolver".equals(arg)) {
                    if (useResolver != null) {
                        fail("You can only specify one of -resolver and -no-resolver");
                    }
                    useResolver = Boolean.FALSE;
                } else if ("-dtd".equals(arg)) {
                    dtdValidate = true;
                } else if ("-help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
                    usage();
                } else if ("-validate".equals(arg)) {
                    validateCatalogs = true;
                } else if (arg.startsWith("-cache:")) {
                    cache = arg.substring(7);
                    useCache = true;
                } else if ("-cache".equals(arg)) {
                    useCache = true;
                } else if (arg.startsWith("-xsd:")) {
                    xsds.add(arg.substring(5));
                } else if (arg.startsWith("-rng:")) {
                    if (rng != null) {
                        fail("Only one -rng: option may be specified");
                    }
                    rng = arg.substring(5);
                } else if (arg.startsWith("-xsl:")) {
                    if (xsl != null) {
                        fail("Only one -xsl: option may be specified");
                    }
                    xsl = arg.substring(5);
                } else if (arg.startsWith("-catalog:")) {
                    catalogs.add(arg.substring(9));
                } else if ("-classpath-catalogs".equals(arg)) {
                    if (classpathCatalogs != null) {
                        fail("You can only specify one of -classpath-catalogs and -no-classpath-catlogs");
                    }
                    classpathCatalogs = Boolean.TRUE;
                } else if ("-no-classpath-catalogs".equals(arg)) {
                    if (classpathCatalogs != null) {
                        fail("You can only specify one of -classpath-catalogs and -no-classpath-catlogs");
                    }
                    classpathCatalogs = Boolean.FALSE;
                } else {
                    fail("Unrecognized option: " + arg);
                }
            } else {
                if (document != null) {
                    fail("Only one document (non-option) may be specified");
                }
                document = arg;
            }
            pos++;
        }

        if (useResolver == null) {
            useResolver = Boolean.TRUE;
        }

        if (classpathCatalogs == null) {
            classpathCatalogs = Boolean.TRUE;
        }

        if (!useResolver && validateCatalogs) {
            fail("Cannot validate catalogs when not using the resolver");
        }

        if (document == null) {
            fail("You must specify a document", 2);
        }

        SampleApp proc = new SampleApp();
        proc.run();
    }

    private static void fail(String message) {
        fail(message, 1);
    }

    private static void fail(String message, int level) {
        if (message != null) {
            System.err.println(message);
        }
        System.exit(level);
    }

    private static void usage() {
        System.out.println("Usage: org.xmlresolver.example.SampleApp [options] document.xml\n" +
                "\n" +
                "Options:\n" +
                "  -dtd           Perform a (DTD) validating parse of the document\n" +
                "  -xsd:file      Perform XML Schema validation with XML Schema ‘file’\n" +
                "  -rng:file      Perform RELAX NG validation with RELAX NG grammar ‘file’\n" +
                "  -xsl:file      Perform an XSLT transformation with stylesheet ‘file’\n" +
                "  -catalog:file  Use ‘file’ as an XML Catalog\n" +
                "  -cache[:dir]   Enable caching (using ‘dir’ as the cache directory)\n" +
                "  -validate      Validate catalog files when they’re loaded\n" +
                "  -[no-]resolver            [Don't] use the resolver (the default)\n" +
                "  -[no-]classpath-catalogs  [Don't] search the classpath for catalogs\n" +
                "  -help                     Print this usage message\n" +
                "\n" +
                "The -catalog and -xsd options may be repeated.\n");
        System.exit(0);
    }

    private Resolver getResolver() {
        // By default the resolver will look for an xmlresolver.properties file on the classpath.
        // I want to make sure I get the sample properties file so I gave it a different name
        // and I'm loading it explicitly.
        URL propurl = ClassLoader.getSystemClassLoader().getResource("xmlresolver-sampleapp.properties");
        if (propurl == null) {
            fail("Configuration error, cannot open xmlresolver-sampleapp.properties file.");
        }
        List<URL> propertyFiles = Collections.singletonList(propurl);
        XMLResolverConfiguration config = new XMLResolverConfiguration(propertyFiles, catalogs);
        config.setFeature(ResolverFeature.CACHE_UNDER_HOME, false);
        config.setFeature(ResolverFeature.CLASSPATH_CATALOGS, classpathCatalogs);

        if (useCache) {
            if (cache != null) {
                config.setFeature(ResolverFeature.CACHE_DIRECTORY, cache);
            }
        } else {
            config.setFeature(ResolverFeature.CACHE_DIRECTORY, null);
        }

        if (validateCatalogs) {
            config.setFeature(ResolverFeature.CATALOG_LOADER_CLASS, "org.xmlresolver.loaders.ValidatingXmlLoader");
        }

        List<String> resCatalogs = config.getFeature(ResolverFeature.CATALOG_FILES);
        if (resCatalogs.isEmpty()) {
            System.out.println("Using the XML Resolver with no catalogs");
        } else {
            System.out.println("Using the XML Resolver with the following catalogs:");
            for (String cat : resCatalogs) {
                System.out.println("\t" + cat);
            }
        }

        if (config.getFeature(ResolverFeature.ALLOW_CATALOG_PI)) {
            System.out.println("OASIS XML Catalogs processing instruction catalogs will be used");
        } else {
            System.out.println("OASIS XML Catalogs processing instruction catlaogs will be ignored");
        }

        ResourceCache cache = config.getFeature(ResolverFeature.CACHE);
        if (cache.directory() != null) {
            System.out.println("Caching to " + cache.directory());
        } else {
            System.out.println("The resolver will not cache resources");
        }

        return new Resolver(config);
    }

    public void run() {
        if (dtdValidate) {
            System.out.println("Performing a (DTD) validating parse of " + document);
        } else {
            System.out.println("Performing a non-validating parse of " + document);
        }
        if (rng != null) {
            System.out.println("Continuing with RELAX NG validation with " + rng);
        }
        if (!xsds.isEmpty()) {
            System.out.println("Continuing with XML Schema validation with:");
            for (String xsd : xsds) {
                System.out.println("\t" + xsd);
            }
        }
        if (xsl != null) {
            System.out.println("Continuing with XSLT transformation with " + xsl);
        }
        if (!useResolver) {
            System.out.println("The XML Resolver *is not* being used!");
        } else {
            if (validateCatalogs) {
                System.out.println("Catalogs will be validated when they are loaded");
            }
        }

        ChattyResolver chattyResolver;
        if (useResolver) {
            chattyResolver = new ChattyResolver(getResolver());
        } else {
            chattyResolver = new ChattyResolver(null);
        }

        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(dtdValidate);
            spf.setNamespaceAware(true);
            SAXParser parser = spf.newSAXParser();
            XMLReader reader = parser.getXMLReader();

            reader.setEntityResolver(chattyResolver);
            InputSource source = new InputSource(document);
            reader.parse(source);
            System.out.println("Parse complete");
        } catch (ParserConfigurationException pce) {
            fail("Could not create a parser. Classpath problem, perhaps?");
        } catch (SAXException se) {
            fail("Could not parse document: " + se.getMessage());
        } catch (IOException ioe) {
            fail("I/O error: " + ioe.getMessage());
        }

        if (rng != null) {
            try {
                PropertyMapBuilder builder = new PropertyMapBuilder();
                builder.put(ValidateProperty.ENTITY_RESOLVER, chattyResolver);
                builder.put(ValidateProperty.URI_RESOLVER, chattyResolver);

                SchemaReader sr = null;
                // Hack. Could have an option for this, or could buffer the schema
                // so that it can be read twice. But for this little sample application,
                // let's just do the easy thing.
                if (rng.toLowerCase().endsWith(".rnc")) {
                    sr = CompactSchemaReader.getInstance();
                }

                ValidationDriver driver = new ValidationDriver(builder.toPropertyMap(), builder.toPropertyMap(), sr);

                Source schemaSource = ((URIResolver) chattyResolver).resolve(rng, null);
                SAXSource schema = (ResolverSAXSource) schemaSource;
                if (schemaSource == null) {
                    URI suri = URI.create("file://" + System.getProperty("user.dir") + "/").resolve(rng);
                    schema = new SAXSource(new InputSource(suri.toString()));
                }

                if (!driver.loadSchema(schema.getInputSource())) {
                    fail("Could not load schema!");
                }
                
                InputSource source = new InputSource(document);
                if (driver.validate(source)) {
                    System.out.println("RELAX NG validation: valid");
                } else {
                    System.out.println("RELAX NG validation: NOT VALID");
                }
            } catch (TransformerException te) {
                fail("Could not lookup URI in catalog: " + te.getMessage());
            } catch (SAXException se) {
                fail("Could not load schema: " + se.getMessage());
            } catch (IOException ioe) {
                fail("I/O error: " + ioe.getMessage());
            }
        }

        if (!xsds.isEmpty()) {
            String schemaDoc = null;
            Source[] schemaSources = null;
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setIgnoringComments(true);
                dbf.setNamespaceAware(true);
                dbf.setValidating(false);
                DocumentBuilder db = dbf.newDocumentBuilder();
                HashMap<String,Source> nsmap = new HashMap<>();
                for (String xsd : xsds) {
                    // Bug in Xerces
                    schemaDoc = xsd.replaceAll(" ", "%20");
                    Document doc = db.parse(schemaDoc);
                    Element docelem = doc.getDocumentElement();
                    String targetNS = docelem.getAttribute("targetNamespace");
                    nsmap.put(targetNS == null ? "" : targetNS, new StreamSource(doc.getBaseURI()));
                }
                schemaSources = nsmap.values().toArray(new Source[0]);
            } catch (ParserConfigurationException pce) {
                fail("Could not configure parser: " + pce.getMessage());
            } catch (SAXException|IOException ex) {
                fail("Could not load schema: " + schemaDoc + ": " + ex.getMessage());
            }

            Schema schemas = null;
            try {
                SchemaFactory sf;
                sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                sf.setResourceResolver(chattyResolver);
                schemas = sf.newSchema(schemaSources);
            } catch (SAXException ex) {
                fail("Could not load XML schema document(s): " + ex.getMessage());
            }

            try {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(true);
                spf.setValidating(false);
                spf.setSchema(schemas);

                SAXParser parser = spf.newSAXParser();
                XMLReader reader = parser.getXMLReader();
                reader.setEntityResolver(chattyResolver);
                ParseHandler handler = new ParseHandler();
                parser.parse(document, handler);
                if (handler.getValid()) {
                    System.out.println("XML Schema validation: valid");
                } else {
                    System.out.println("XML Schema validation: NOT VALID");
                }
            } catch (ParserConfigurationException pce) {
                fail("Could not configure parser: " + pce.getMessage());
            } catch (SAXException|IOException ex) {
                fail("Could not load schema: " + schemaDoc + ": " + ex.getMessage());
            }
        }

        if (xsl != null) {
            Processor processor = new Processor(false);
            Configuration config = processor.getUnderlyingConfiguration();
            config.setSourceParserClass("org.xmlresolver.tools.ResolvingXMLReader");
            config.setStyleParserClass("org.xmlresolver.tools.ResolvingXMLReader");
            System.setProperty("xmlresolver.properties", System.getProperty("user.dir") + "/src/main/resources/XMLResolver.properties");
            config.setURIResolver(chattyResolver);

            try {
                InputSource docsrc = new InputSource(document);
                XsltCompiler compiler = processor.newXsltCompiler();
                FileInputStream fis = new FileInputStream(xsl);
                XsltExecutable exec = compiler.compile(new StreamSource(fis, xsl));
                Xslt30Transformer transformer = exec.load30();
                XdmDestination destination = new XdmDestination();
                transformer.transform(new SAXSource(docsrc), destination);
                System.out.println("Done");
            } catch (SaxonApiException|IOException sae) {
                fail("Transformation failed: " + sae.getMessage());
            }
        }
    }

    private static class ParseHandler extends DefaultHandler {
        private boolean valid = true;

        public boolean getValid() {
            return valid;
        }

        @Override
        public void fatalError(SAXParseException ex) throws SAXException {
            valid = false;
            System.err.println(ex.getMessage());
        }

        @Override
        public void error(SAXParseException ex) throws SAXException {
            valid = false;
            System.err.println(ex.getMessage());
        }

        @Override
        public void warning(SAXParseException ex) throws SAXException {
            System.err.println(ex.getMessage());
        }
    }
}