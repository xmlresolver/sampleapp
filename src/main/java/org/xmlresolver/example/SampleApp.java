package org.xmlresolver.example;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
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
import org.xmlresolver.CatalogManager;
import org.xmlresolver.Resolver;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.cache.ResourceCache;
import org.xmlresolver.catalog.entry.Entry;
import org.xmlresolver.catalog.entry.EntryCatalog;
import org.xmlresolver.exceptions.CatalogInvalidException;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SampleApp {
    public static void main(String[] args) {
        SampleApp app = new SampleApp();
        app.process(args);
    }

    private void process(String[] args) {
        CommandMain cmain = new CommandMain();
        CommandParse cparse = new CommandParse();
        CommandLookup clookup = new CommandLookup();
        CommandShow cshow = new CommandShow();
        JCommander jc = JCommander.newBuilder()
                .addObject(cmain)
                .addCommand("parse", cparse)
                .addCommand("lookup", clookup)
                .addCommand("show", cshow)
                .build();

        jc.setProgramName("SampleApp");

        try {
            jc.parse(args);
            if (cmain.help || jc.getParsedCommand() == null) {
                usage(jc, true);
            } else {
                cmain.command = jc.getParsedCommand();

                if (!cmain.resolver && cmain.validate) {
                    throw new ParameterException("The resolver must be enabled for the -validate option");
                }

                if (cmain.cacheDirectory != null) {
                    cmain.cache = true;
                }

                switch (jc.getParsedCommand()) {
                    case "parse":
                        parse(cmain, cparse);
                        break;
                    case "lookup":
                        lookup(cmain, clookup);
                        break;
                    case "show":
                        show(cmain, cshow);
                        break;
                    default:
                        throw new UnsupportedOperationException("Unexpected command: " + jc.getParsedCommand());
                }
            }
        } catch (ParameterException pe) {
            System.err.println(pe.getMessage());
            usage(pe.getJCommander(), false);
        }
    }

    private void usage(JCommander jc, boolean help) {
        if (jc != null) {
            DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
            StringBuilder sb = new StringBuilder();
            formatter.usage(sb);
            System.err.println(sb);
        }
        if (help) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    private void parse(CommandMain main, CommandParse command) {
        if (command.dtd) {
            System.out.println("Performing a (DTD) validating parse of " + command.document);
        } else {
            System.out.println("Performing a non-validating parse of " + command.document);
        }

        if (command.grammar != null) {
            System.out.println("Continuing with RELAX NG validation with " + command.grammar);
        }

        if (!command.schemas.isEmpty()) {
            System.out.println("Continuing with XML Schema validation with:");
            for (String xsd : command.schemas) {
                System.out.println("\t" + xsd);
            }
        }

        if (command.xsl != null) {
            System.out.println("Continuing with XSLT transformation with " + command.xsl);
        }

        ChattyResolver chattyResolver;
        if (!main.resolver) {
            System.out.println("The XML Resolver *is not* being used!");
            chattyResolver = new ChattyResolver(null);
        } else {
            chattyResolver = new ChattyResolver(getResolver(main));
        }

        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(command.dtd);
            spf.setNamespaceAware(true);
            SAXParser parser = spf.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            ParseHandler handler = new ParseHandler();
            reader.setErrorHandler(handler);
            reader.setEntityResolver(chattyResolver);
            InputSource source = new InputSource(command.document);
            reader.parse(source);
            System.out.println("Parse complete");
        } catch (ParserConfigurationException pce) {
            throw new ParameterException("Could not create a parser. Classpath problem, perhaps?");
        } catch (SAXException se) {
            throw new ParameterException("Could not parse document: " + se.getMessage());
        } catch (IOException ioe) {
            throw new ParameterException("I/O error: " + ioe.getMessage());
        } catch (CatalogInvalidException cie) {
            throw new ParameterException(cie.getMessage());
        }

        if (command.grammar != null) {
            try {
                PropertyMapBuilder builder = new PropertyMapBuilder();
                builder.put(ValidateProperty.ENTITY_RESOLVER, chattyResolver);
                builder.put(ValidateProperty.URI_RESOLVER, chattyResolver);

                SchemaReader sr = null;
                // Hack. Could have an option for this, or could buffer the schema
                // so that it can be read twice. But for this little sample application,
                // let's just do the easy thing.
                if (command.grammar.toLowerCase().endsWith(".rnc")) {
                    sr = CompactSchemaReader.getInstance();
                }

                ValidationDriver driver = new ValidationDriver(builder.toPropertyMap(), builder.toPropertyMap(), sr);

                Source schemaSource = ((URIResolver) chattyResolver).resolve(command.grammar, null);
                SAXSource schema = (ResolverSAXSource) schemaSource;
                if (schemaSource == null) {
                    URI suri = URI.create("file://" + System.getProperty("user.dir") + "/").resolve(command.grammar);
                    schema = new SAXSource(new InputSource(suri.toString()));
                }

                if (!driver.loadSchema(schema.getInputSource())) {
                    throw new ParameterException("Could not load schema!");
                }

                InputSource source = new InputSource(command.document);
                if (driver.validate(source)) {
                    System.out.println("RELAX NG validation: valid");
                } else {
                    System.out.println("RELAX NG validation: NOT VALID");
                }
            } catch (TransformerException te) {
                throw new ParameterException("Could not lookup URI in catalog: " + te.getMessage());
            } catch (SAXException se) {
                throw new ParameterException("Could not load schema: " + se.getMessage());
            } catch (IOException ioe) {
                throw new ParameterException("I/O error: " + ioe.getMessage());
            }
        }

        if (!command.schemas.isEmpty()) {
            String schemaDoc = null;
            Source[] schemaSources = null;
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setIgnoringComments(true);
                dbf.setNamespaceAware(true);
                dbf.setValidating(false);
                DocumentBuilder db = dbf.newDocumentBuilder();
                HashMap<String, Source> nsmap = new HashMap<>();
                for (String xsd : command.schemas) {
                    // Bug in Xerces
                    schemaDoc = xsd.replaceAll(" ", "%20");
                    Document doc = db.parse(schemaDoc);
                    Element docelem = doc.getDocumentElement();
                    String targetNS = docelem.getAttribute("targetNamespace");
                    nsmap.put(targetNS == null ? "" : targetNS, new StreamSource(doc.getBaseURI()));
                }
                schemaSources = nsmap.values().toArray(new Source[0]);
            } catch (ParserConfigurationException pce) {
                throw new ParameterException("Could not configure parser: " + pce.getMessage());
            } catch (SAXException | IOException ex) {
                throw new ParameterException("Could not load schema: " + schemaDoc + ": " + ex.getMessage());
            }

            Schema schemas = null;
            try {
                SchemaFactory sf;
                sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                sf.setResourceResolver(chattyResolver);
                schemas = sf.newSchema(schemaSources);
            } catch (SAXException ex) {
                throw new ParameterException("Could not load XML schema document(s): " + ex.getMessage());
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
                parser.parse(command.document, handler);
                if (handler.getValid()) {
                    System.out.println("XML Schema validation: valid");
                } else {
                    System.out.println("XML Schema validation: NOT VALID");
                }
            } catch (ParserConfigurationException pce) {
                throw new ParameterException("Could not configure parser: " + pce.getMessage());
            } catch (SAXException | IOException ex) {
                throw new ParameterException("Could not load schema: " + schemaDoc + ": " + ex.getMessage());
            }
        }

        if (command.xsl != null) {
            Processor processor = new Processor(false);
            Configuration config = processor.getUnderlyingConfiguration();
            config.setSourceParserClass("org.xmlresolver.tools.ResolvingXMLReader");
            config.setStyleParserClass("org.xmlresolver.tools.ResolvingXMLReader");
            System.setProperty("xmlresolver.properties", System.getProperty("user.dir") + "/src/main/resources/XMLResolver.properties");
            config.setURIResolver(chattyResolver);

            try {
                InputSource docsrc = new InputSource(command.document);
                XsltCompiler compiler = processor.newXsltCompiler();
                FileInputStream fis = new FileInputStream(command.xsl);
                XsltExecutable exec = compiler.compile(new StreamSource(fis, command.xsl));
                Xslt30Transformer transformer = exec.load30();
                XdmDestination destination = new XdmDestination();
                transformer.transform(new SAXSource(docsrc), destination);
                System.out.println("Done");
            } catch (SaxonApiException | IOException sae) {
                throw new ParameterException("Transformation failed: " + sae.getMessage());
            }
        }
    }

    private void lookup(CommandMain main, CommandLookup command) {
        if (!main.resolver) {
            throw new ParameterException("The resolver must be enabled for the lookup command");
        }

        if (command.publicId == null && command.systemId == null && command.name == null && command.uri == null) {
            throw new ParameterException("You must specify at least one of -uri:, -system:, -public:, or -name:");
        }

        if ((command.publicId != null || command.systemId != null) && command.uri != null) {
            throw new ParameterException("You must specify either -system: (and optionally -public:) or -uri: for lookup, not both");
        }

        if (command.name != null && command.uri != null) {
            throw new ParameterException("The -name: option applies to system identifiers, not uris");
        }

        Resolver resolver = getResolver(main);
        CatalogManager manager = resolver.getConfiguration().getFeature(ResolverFeature.CATALOG_MANAGER);

        String style = command.lookupType;
        if (style == null) {
            if (command.uri != null) {
                style = "uri";
                if (command.nature != null || command.purpose != null) {
                    style = "namespace";
                }
            } else {
                style = "entity";
            }
        }

        URI resolved = null;
        switch (style) {
            case "namespace":
                if (command.name != null) {
                    throw new ParameterException("The -name: option doesn't apply to namespace queries.");
                }
                System.out.println("Performing namespace lookup...");
                resolved = manager.lookupNamespaceURI(command.uri, command.nature, command.purpose);
                break;
            case "uri":
                System.out.println("Performing URI lookup...");
                resolved = manager.lookupURI(command.uri);
                break;
            case "entity":
                System.out.println("Performing entity lookup...");
                resolved = manager.lookupEntity(command.name, command.systemId, command.publicId);
                break;
            case "public":
                if (command.name != null) {
                    throw new ParameterException("The -name: option doesn't apply to public queries.");
                }
                System.out.println("Performing public lookup...");
                resolved = manager.lookupPublic(command.systemId, command.publicId);
                break;
            case "system":
                if (command.publicId != null) {
                    throw new ParameterException("The -public: option doesn't apply to system queries.");
                }
                System.out.println("Performing system lookup...");
                resolved = manager.lookupSystem(command.systemId);
                break;
            case "doctype":
                System.out.println("Performing doctype lookup...");
                resolved = manager.lookupDoctype(command.name, command.systemId, command.publicId);
                break;
            case "document":
                if (command.name != null || command.systemId != null || command.publicId != null || command.uri != null
                    || command.nature != null || command.purpose != null) {
                    throw new ParameterException("Document queries don't take any options");
                }
                System.out.println("Performing document lookup...");
                resolved = manager.lookupDocument();
                break;
            case "notation":
                System.out.println("Performing notation lookup...");
                resolved = manager.lookupNotation(command.name, command.systemId, command.publicId);
                break;
            default:
                throw new ParameterException("Unknown query type: " + style);
        }

        if (resolved == null) {
            System.out.println("Failed to find matching catalog entry.");
        } else {
            if ("file".equals(resolved.getScheme())) {
                System.out.println("Resolves to: " + resolved.getPath());
            } else {
                System.out.println("Resolves to: " + resolved);
            }
        }
    }

    private void show(CommandMain main, CommandShow command) {
        if (!main.resolver) {
            throw new ParameterException("The resolver must be enabled for the show command");
        }

        Resolver resolver = getResolver(main);
        String regex = ".*";
        if (command.regex != null) {
            regex = command.regex;
            System.out.println("Showing all catalog entries matching " + regex);
        }

        XMLResolverConfiguration config = resolver.getConfiguration();
        List<String> resCatalogs = config.getFeature(ResolverFeature.CATALOG_FILES);

        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        CatalogManager manager = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        for (String cat : resCatalogs) {
            System.out.println(cat);
            int count = 0;
            int match = 0;
            EntryCatalog ecat =  manager.loadCatalog(URIUtils.resolve(URIUtils.cwd(), cat));
            for (Entry entry : ecat.entries()) {
                count += 1;
                String str = entry.toString();
                Matcher matcher = pattern.matcher(str);
                if (matcher.find()) {
                    System.out.println("  " + str);
                    match += 1;
                }
            }
            if (!".*".equals(regex)) {
                System.out.println(match + " of " + count + " matches");
            }
        }
    }

    private Resolver getResolver(CommandMain main) {
        // By default the resolver will look for an xmlresolver.properties file on the classpath.
        // I want to make sure I get the sample properties file so I gave it a different name
        // and I'm loading it explicitly.
        URL propurl = ClassLoader.getSystemClassLoader().getResource("xmlresolver-sampleapp.properties");
        if (propurl == null) {
            throw new ParameterException("Configuration error, cannot open xmlresolver-sampleapp.properties file.");
        }
        List<URL> propertyFiles = Collections.singletonList(propurl);
        XMLResolverConfiguration config = new XMLResolverConfiguration(propertyFiles, main.catalogs);
        config.setFeature(ResolverFeature.CACHE_UNDER_HOME, false);
        config.setFeature(ResolverFeature.CLASSPATH_CATALOGS, main.classpathCatalogs);

        if (main.cache) {
            if (main.cacheDirectory != null) {
                config.setFeature(ResolverFeature.CACHE_DIRECTORY, main.cacheDirectory);
            }
        } else {
            config.setFeature(ResolverFeature.CACHE_DIRECTORY, null);
        }

        if (main.validate) {
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

        if ("parse".equals(main.command)) {
            // Catalog PIs are only relevant when parsing
            if (config.getFeature(ResolverFeature.ALLOW_CATALOG_PI)) {
                System.out.println("OASIS XML Catalogs processing instruction catalogs will be used");
            } else {
                System.out.println("OASIS XML Catalogs processing instruction catlaogs will be ignored");
            }
        }

        ResourceCache cache = config.getFeature(ResolverFeature.CACHE);
        if (cache.directory() != null) {
            System.out.println("Cache location: " + cache.directory());
        } else {
            if (main.cache) {
                if (main.cacheDirectory == null) {
                    throw new ParameterException("Failed to initialize cache");
                } else {
                    throw new ParameterException("Failed to initialize cache: " + main.cacheDirectory);
                }
            }

            if ("parse".equals(main.command)) {
                // Not parsing is only relevant to the parse command
                System.out.println("The resolver will not cache resources");
            }
        }

        System.out.println();

        return new Resolver(config);
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

    @Parameters(separators = ":", commandDescription = "Global options")
    private static class CommandMain {
        private String command = null;

        @Parameter(names = {"-help", "-h", "--help"}, help = true, description = "Display help")
        private boolean help = false;

        @Parameter(names = "-catalog", description = "Use XML Catalog for resolution")
        private List<String> catalogs = new ArrayList<>();

        @Parameter(names = "-validate", description = "Validate catalog files")
        private boolean validate = false;

        @Parameter(names = "-resolver", description = "Use the XML Resolver during processing", arity = 1)
        private boolean resolver = true;

        @Parameter(names = {"-classpath-catalogs", "-cp"}, description = "Search the classpath for catalogs", arity = 1)
        private boolean classpathCatalogs = true;

        @Parameter(names = "-cache", description = "Enable caching")
        private boolean cache = false;

        @Parameter(names = {"-cache-directory", "-cache-dir"}, description = "Directory to use for caching (implies -cache)")
        private String cacheDirectory;
    }

    @Parameters(separators = ":", commandDescription = "Parse, validate, and/or transform a document")
    private static class CommandParse {
        @Parameter(description = "The document to process", required = true)
        private String document;

        @Parameter(names = "-dtd", description = "Perform a (DTD) validating parse")
        private boolean dtd = false;

        @Parameter(names = "-xsd", description = "Perform XML Schema validation with schema(s)")
        private List<String> schemas = new ArrayList<>();

        @Parameter(names = "-rng", description = "Perform RELAX NG validation with grammar")
        private String grammar;

        @Parameter(names = "-xsl", description = "Transform the document wht the XSL stylesheet")
        private String xsl;
    }

    @Parameters(separators = ":", commandDescription = "Lookup entries in the catalog(s)")
    private static class CommandLookup {
        @Parameter(names = "-type", description = "Perform lookup of a particular type", converter = LookupTypeConverter.class)
        private String lookupType;

        @Parameter(names = "-name", description = "Specify the doctype or entity name")
        private String name;

        @Parameter(names = "-system", description = "Specify the system identifier")
        private String systemId;

        @Parameter(names = "-public", description = "Specify the public identifier")
        private String publicId;

        @Parameter(names = "-uri", description = "Specify the URI")
        private String uri;

        @Parameter(names = "-nature", description = "Specify the namespace nature")
        private String nature;

        @Parameter(names = "-purpose", description = "Specify the namespace purpose")
        private String purpose;
    }

    @Parameters(separators = ":", commandDescription = "Show the content of the catalog(s)")
    private class CommandShow {
        @Parameter(names = {"-regex", "-r"}, description = "A regular expression to filter the entries shown")
        private String regex;
    }

    private static class LookupTypeConverter implements IStringConverter<String> {
        private static final HashSet<String> validTypes
                = new HashSet<>(Arrays.asList("doctype", "document", "entity", "namespace", "notation", "public", "system", "uri"));
        @Override
        public String convert(String value) {
            if (validTypes.contains(value)) {
                return value;
            } else {
                throw new ParameterException("Invalid lookup type: " + value);
            }
        }
    }
}
