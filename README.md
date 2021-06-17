# XML Resolver Sample Application

This repository contains a sample application that uses the XML
Resolver. For users, it demonstrates many of the features of the
resolver. For developers, it provides some examples of how to
integrate the resolver into other applications.

## What is it?

A simple application that performs

1. Well-formed or validating parses of XML documents.
2. XML Schema validation with Xerces.
3. RELAX NG validation with Jing.
4. XSLT transformations with Saxon-HE.

Parsing XML documents demonstrates how external identifiers are
resolved. The other features demonstrate various forms of URI
resolution.

## What does it do?

The point of the application is to demonstrate how the resolver works;
the validation outcomes and XSLT results are not the
most important part.

The application installs a very “chatty” resolver that will print
information about each request, how it was processed, and what was
returned. For example:

```
✗ Resolved: [dtd]: https://example.com/sample.dtd (-//Example//DTD Sample v1.0//EN)
```

Indicating that an attempt was made to find a DTD with the system
identifier `https://example.com/sample.dtd` and the public identifier
`-//Example//DTD Sample v1.0//EN`, but resolution failed. There was no
match in any of the catalogs searched.

In the case of a successful resolution, the details of the resolution
are provided. For example:

```
✓ Resolved: [dtd]: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
        as: file:/Projects/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
      from: file:/Projects/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
```

The “as” and “from” will be different in the case where a resource is cached or
comes from a `jar:` or `classpath:` URI.

## Command line options

Running the application with no arguments will print a summary of the
command line options:

```
$ java -jar sampleapp-3.0.1beta3.jar
Usage: org.xmlresolver.example.SampleApp [options] document.xml

Options:
  -dtd           Perform a (DTD) validating parse of the document
  -xsd:file      Perform XML Schema validation with XML Schema ‘file’
  -rng:file      Perform RELAX NG validation with RELAX NG grammar ‘file’
  -xsl:file      Perform an XSLT transformation with stylesheet ‘file’
  -catalog:file  Use ‘file’ as an XML Catalog
  -cache[:dir]   Enable caching (using ‘dir’ as the cache directory)
  -validate      Validate catalog files when they’re loaded
  -[no-]resolver            [Don't] use the resolver (the default)
  -[no-]classpath-catalogs  [Don't] search the classpath for catalogs
  -help                     Print this usage message

The -catalog and -xsd options may be repeated.
```

The filename you provide will be parsed and processed according to the
options provided:

* `-dtd` enables DTD validation during the initial parse
* `-xsd` enables XML Schema validation with the specified schema(s)
* `-rng` enables RELAX NG validation with the specified grammar
* `-xsl` enables XSLT transformation with the specified stylesheet
* `-catalog` allows you to specify one or more catalogs to use
* `-cache` enables caching. If you specify a directory, it will be used
  as the cache directory. If you don’t specify a directory, the default
  is `./xmlresolver-cache`.
* `-validate` enables validation *of the XML Catalog files*; this has nothing
  to do with the main document being processed
* `-no-resolver` will disable the resolver entirely. By default, the resolver
  will be used.
* `-no-classpath-catalogs` will disable automatic searching for catalogs on
  the classpath. By default, the resolver will search for catalogs in
  `org/xmlresolver/catalog.xml` on the classpath.

## Examples

For additional information about the resolver and XML Catalogs, see
[https://xmlresolver.org/](https://xmlresolver.org). 

### DTD examples

The [Journal Article Tag Suite](https://jats.nlm.nih.gov/) (JATS) is a
popular DTD-based vocabulary.

#### DTD parsing without a catalog

If you simply parse a JATS document, it will download all of the required 
DTD files:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd xml/jats/doc.xml
Performing a (DTD) validating parse of xml/jats/doc.xml
✗ Resolved: [dtd]: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
✗ Resolved: %articleauthcustom-modules.ent: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthcustom-modules1.ent (-//NLM//DTD JATS (Z39.96) Article Authoring DTD-Specific Modules v1.2 20190208//EN)
✗ Resolved: %modules.ent: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-modules1.ent (-//NLM//DTD JATS (Z39.96) JATS DTD Suite Module of Modules v1.2 20190208//EN)
…
```

This downloads a little over a megabyte of data and takes 10-20
seconds with a fast internet connection. 

#### DTD parsing with a catalog

The distribution includes a local copy of the JATS DTD files and a
catalog that uses them:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -catalog:schema/jats/catalog.xml xml/jats/doc.xml
Performing a (DTD) validating parse of xml/jats/doc.xml
✓ Resolved: [dtd]: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
✓ Resolved: %articleauthcustom-modules.ent: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthcustom-modules1.ent (-//NLM//DTD JATS (Z39.96) Article Authoring DTD-Specific Modules v1.2 20190208//EN)
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthcustom-modules1.ent
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthcustom-modules1.ent
…
```

This requires no internet connection and finishes in a second or so.

#### DTD parsing with a rewrite catalog

If you look at the `schema/jats/catalog.xml` catalog, you’ll see that
it contains a lot of entries. This can be somewhat tedious to manage,
although in the case of stable schemas like JATS, it only has to be
done once.

In many cases, all of the schema documents are in a single directory,
or a tree with a single root. If this is the case, it’s possible to
simplify the catalog to just a single “rewrite” entry.

There’s one in `schema/jats/rewrite.xml`:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -catalog:schema/jats/rewrite.xml xml/jats/doc.xml
✓ Resolved: [dtd]: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthoring1.dtd
✗ Resolved: %articleauthcustom-modules.ent: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-articleauthcustom-modules1.ent (-//NLM//DTD JATS (Z39.96) Article Authoring DTD-Specific Modules v1.2 20190208//EN)
✗ Resolved: %modules.ent: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/JATS-modules1.ent (-//NLM//DTD JATS (Z39.96) JATS DTD Suite Module of Modules v1.2 20190208//EN)
…
```

What you’ll see that’s different here is that after the DTD is
resolved, resolution “fails” for all of the subsequent requests, but
that’s ok. The original URI has been rewritten to the local path and
the whole tree of schema documents have been stored on the filesystem
so they all resolve to the correct documents with the `file:` URIs.

#### DTD parsing with a cache

Another way to take advantage of catalogs without having to maintain them yourself
is to use the caching feature:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -cache xml/jats/doc.xml
✓ Resolved: [dtd]: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
        as: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthoring1.dtd
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xmlresolver-cache/data/7be3429ebd754b3978ae10454ea09856dce0c83e1d11eb2400939b9cfa9246c5.dtd
✓ Resolved: %articleauthcustom-modules.ent: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthcustom-modules1.ent (-//NLM//DTD JATS (Z39.96) Article Authoring DTD-Specific Modules v1.2 20190208//EN)
        as: https://jats.nlm.nih.gov/articleauthoring/1.2/JATS-articleauthcustom-modules1.ent
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xmlresolver-cache/data/5dd4017f2c12b7c5f713be5f52eee7e2f5bb55b29e1a9b908058aefe5b616b80.ent
…
```

Like [the very first example](#dtd-parsing-without-a-catalog), this
will take 10-20 seconds (depending on your internet connection) the
first time. But subsequent requests will be much faster.

Here you can see that the resolver “lies” about the resolved URI. It
has to do this because there’s no logical mapping between the local
URIs and the requested URIs.

#### DTD parsing without the classpath catalogs

Keen observers may have noticed that some of the URIs returned in the
rewrite catalog example actually came from a jar file and not from the
JATS directory:

```
✓ Resolved: %ent-mmlextra: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/mathml/mmlextra.ent (-//W3C//ENTITIES Extra for MathML 2.0//EN)
        as: mathml/mmlextra.ent
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/lib/xmlresolver-3.0.1beta3-SNAPSHOT-data.jar!/org/xmlresolver/www.w3.org/Math/DTD/mathml2/mathml/mmlextra.ent
```

The XML Resolver ships with a “data” jar file that contains a large
selection of commonly used, public schemas. Here, the public
identifier for the MathML 2.0 extra entities was found in the data jar
catalog and used to resolve the entity. If you tell the resolver not
to search for catalogs on the classpath, this won’t happen:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -catalog:schema/jats/rewrite.xml xml/jats/doc.xml
…
✗ Resolved: %ent-mmlextra: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/jats/1.2/mathml/mmlextra.ent (-//W3C//ENTITIES Extra for MathML 2.0//EN)
…
```

The document is still opened locally because the system identifier is
already relative from the originally resolved DTD.

#### Parse DTDs from the W3C

The W3C imposes a ten second penalty on requests for many commonly
used schema documents. (If you ignore the penalty and pound on the W3C
servers anyway, you’ll eventually get locked out entirely.)

You can see this if you try to parse an XHTML document:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -no-classpath-catalogs xml/xhtml/index.xhtml
Performing a (DTD) validating parse of xml/xhtml/index.xhtml
✗ Resolved: [dtd]: https://www.w3.org/MarkUp/DTD/xhtml11.dtd (-//W3C//DTD XHTML 1.1//EN)
✗ Resolved: %xhtml-inlstyle.mod: http://www.w3.org/MarkUp/DTD/xhtml-inlstyle-1.mod (-//W3C//ELEMENTS XHTML Inline Style 1.0//EN)
…
```

This takes almost *ten minutes* because of the delay imposed on every
request. (This isn’t unique to XHTML, the same restriction applies to
XML Schema documents and other resources.)

Try it again, letting the resolver use the data jar, and it takes only a
fraction of a second:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd xml/xhtml/index.xhtml
✓ Resolved: [dtd]: https://www.w3.org/MarkUp/DTD/xhtml11.dtd (-//W3C//DTD XHTML 1.1//EN)
        as: https://www.w3.org/MarkUp/DTD/xhtml11.dtd
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/lib/xmlresolver-3.0.1beta3-SNAPSHOT-data.jar!/org/xmlresolver/www.w3.org/MarkUp/DTD/xhtml11.dtd
✓ Resolved: %xhtml-inlstyle.mod: http://www.w3.org/TR/xhtml-modularization/DTD/xhtml-inlstyle-1.mod (-//W3C//ELEMENTS XHTML Inline Style 1.0//EN)
        as: http://www.w3.org/TR/xhtml-modularization/DTD/xhtml-inlstyle-1.mod
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/lib/xmlresolver-3.0.1beta3-SNAPSHOT-data.jar!/org/xmlresolver/www.w3.org/MarkUp/DTD/xhtml-inlstyle-1.mod
…
```

#### Explicit classpath catalogs

If for some reason you didn’t want to let the resolver search the classpath, but you
still wanted fast access to the data jar resources, you can specify the catalog
explicitly:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -no-classpath-catalogs \
       -catalog:jar:file:lib/xmlresolver-3.0.1beta3-SNAPSHOT-data.jar!/org/xmlresolver/catalog.xml \
       xml/xhtml/index.xhtml
…
```

(Depending on your shell, you may have to escape the “!” in the `jar:` URI.)

### Catching catalog errors

Usually, the resolver ignores catalog errors. You don’t want your production application
falling over because someone puts a typo in a catalog:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -catalog:schema/caterror.xml xml/xhtml/index.xhtml
✓ Resolved: [dtd]: https://www.w3.org/MarkUp/DTD/xhtml11.dtd (-//W3C//DTD XHTML 1.1//EN)
        as: https://www.w3.org/MarkUp/DTD/xhtml11.dtd
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/lib/xmlresolver-3.0.1beta3-SNAPSHOT-data.jar!/org/xmlresolver/www.w3.org/MarkUp/DTD/xhtml11.dtd
…
```

However, catalog errors are a common cause of resolution failures. You can tell the
resolver to process catalogs with a validating parser. This will throw an exception
if an invalid catalog is loaded:

```
$ java -jar sampleapp-3.0.1beta3.jar -dtd -validate -catalog:schema/caterror.xml xml/xhtml/index.xhtml
Exception in thread "main" org.xmlresolver.exceptions.CatalogInvalidException: Catalog 'file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/caterror.xml' is invalid: attribute "systemid" not allowed here; expected attribute "id", "systemId" or "uri" or an attribute from another namespace
	at org.xmlresolver.loaders.ValidatingXmlLoader.loadCatalog(ValidatingXmlLoader.java:147)
…
```

### RELAX NG validation

Using a catalog for RELAX NG validation is much the same as for DTDs except that the
`uri` catalog entry is used instead of the `system` entry. And, of course, there’s no
public identifier involved.

### RELAX NG validation with an explicit catalog

There’s a sample schema in RELAX NG in the distribution:

```
$ java -jar sampleapp-3.0.1beta3.jar -catalog:schema/sample/catalog.xml -rng:schema/sample/sample.rnc xml/sample/doc.xml
✗ Resolved: book (file:///Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/sample/doc.xml)
Parse complete
✗ Resolved: schema/sample/sample.rnc
✓ Resolved: https://xmlresolver.org/ns/sample/blocks.rnc (file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/sample.rnc)
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/blocks.rnc
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/blocks.rnc
RELAX NG validation: valid
```

### RELAX NG validation with a classpath catalog

The same trick that applies to the W3C schemas in the “data” jar also applies to
user-supplied jar files. For example, the DocBook schema jar file comes with
a catalog that the XML Resolver will find.

I haven’t made the DocBook jar files explicit dependencies because they’re only
really needed for the few examples that use DocBook. For this example, we have to
construct the call to java with an explicit classpath:

```
$ java -cp docbook/schemas-docbook-5.2b10a4.jar\
:sampleapp-3.0.1beta3.jar org.xmlresolver.example.SampleApp \
-rng:xml/docbook/schema.rng xml/docbook/doc.xml
✗ Resolved: article (file:///Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/docbook/doc.xml)
Parse complete
✗ Resolved: xml/docbook/schema.rng
✓ Resolved: https://docbook.org/xml/5.1/rng/docbook.rng (file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/docbook/schema.rng)
        as: https://docbook.org/xml/5.1/rng/docbook.rng
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/docbook/schemas-docbook-5.2b10a4.jar!/org/docbook/schemas/docbook/5.1/rng/docbook.rng
```

(You can type that all one line if you leave out the backslashes, just be careful
not to put spaces around the “:” characters in the classpath.)

Note that we got automatic catalog resolution of the RELAX NG grammar from the
presence of the jar file!

### XML Schema validation

Using a catalog for XML Schema validation is also much the same.

### XML Schema validation with an explicit catalog

There’s a sample XML Schema in the distribution:

```
$ java -jar sampleapp-3.0.1beta3.jar -catalog:schema/sample/catalog.xml -xsd:schema/sample/sample.rnc xml/sample/doc.xml
✗ Resolved: book (file:///Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/sample/doc.xml)
Parse complete
✓ Resolved: http://www.w3.org/2001/XMLSchema: https://xmlresolver.org/ns/sample/blocks.xsd (http://xmlresolver.com/ns/sample)
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/blocks.xsd
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/blocks.xsd
✗ Resolved: http://www.w3.org/2001/XMLSchema: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/xlink.xsd (http://www.w3.org/1999/xlink)
✓ Resolved: http://www.w3.org/2001/XMLSchema: http://www.w3.org/2001/xml.xsd (http://www.w3.org/XML/1998/namespace)
        as: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/xml.xsd
      from: file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/schema/sample/xml.xsd
XML Schema validation: valid
```

## XSLT Transformations

Just for completeness, here’s an example that uses the DocBook xslTNG jar file to
resolve stylesheet URIs:

```
$ java -cp docbook/docbook-xslTNG-1.5.0.jar\
:docbook/schemas-docbook-5.2b10a4.jar\
:sampleapp-3.0.1beta3.jar org.xmlresolver.example.SampleApp \
-rng:xml/docbook/schema.rng -xsl:xml/docbook/style.xsl  xml/docbook/doc.xml
✗ Resolved: article (file:///Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/docbook/doc.xml)
Parse complete
✗ Resolved: xml/docbook/schema.rng
✓ Resolved: https://docbook.org/xml/5.1/rng/docbook.rng (file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/docbook/schema.rng)
        as: https://docbook.org/xml/5.1/rng/docbook.rng
      from: jar:file:/Users/ndw/Projects/xmlresolver/sampleapp/build/stage/docbook/schemas-docbook-5.2b10a4.jar!/org/docbook/schemas/docbook/5.1/rng/docbook.rng
RELAX NG validation: valid
✓ Resolved: https://cdn.docbook.org/release/xsltng/current/xslt/docbook.xsl (file:///Users/ndw/Projects/xmlresolver/sampleapp/build/stage/xml/docbook/style.xsl)
        as: https://cdn.docbook.org/release/xsltng/current/xslt/docbook.xsl
      from: classpath:org/docbook/xsltng/xslt/docbook.xsl
✓ Resolved: main.xsl (https://cdn.docbook.org/release/xsltng/current/xslt/docbook.xsl)
        as: https://cdn.docbook.org/release/xsltng/current/xslt/main.xsl
      from: classpath:org/docbook/xsltng/xslt/main.xsl
…
Done
```

## What is the other jar file?

The distribution also contains a copy of the “apps” jar from the XML
Resolver. This jar contains the “CacheDetails” application that will
show you what is in your XML Resolver cache, if you have one.

Try:

```
$ java -jar xmlresolver-3.0.1beta3-apps.jar ./resolver-cache
Cache contains 73 entries
…
```
