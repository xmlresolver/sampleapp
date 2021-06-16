package org.xmlresolver.example;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;
import org.xmlresolver.sources.ResolverInputSource;
import org.xmlresolver.sources.ResolverLSInput;
import org.xmlresolver.sources.ResolverSAXSource;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.io.IOException;
import java.net.URI;

/** A very chatty resolver.
 *
 * <p>This resolver is just a wrapper around the real resolver. It prints very
 * chatty messages about the results of attempts to resolve system identifiers
 * and URIs.</p>
 */

public class ChattyResolver implements EntityResolver2, URIResolver, LSResourceResolver {
    private final EntityResolver parent;

    public ChattyResolver(EntityResolver parent) {
        this.parent = parent;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        InputSource source = null;
        if (parent != null) {
            source = parent.resolveEntity(publicId, systemId);
        }
        if (source instanceof ResolverInputSource) {
            if (publicId == null) {
                System.out.println("✓ Resolved: " + systemId);
            } else {
                System.out.println("✓ Resolved: " + systemId + " (" + publicId + ")");
            }
            System.out.println("        as: " + source.getSystemId());
            System.out.println("      from: " + ((ResolverInputSource) source).resolvedURI);
        } else {
            if (!systemId.startsWith("file:")) {
                System.out.println("✗ Resolved: " + systemId);
            }
        }
        return source;
    }

    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        InputSource source = null;
        if (parent != null) {
            source = ((EntityResolver2) parent).getExternalSubset(name, baseURI);
        }
        if (source instanceof ResolverInputSource) {
            if (baseURI == null) {
                System.out.println("✓ Resolved: " + name);
            } else {
                if (name == null) {
                    System.out.println("✓ Resolved: " + baseURI);
                } else {
                    System.out.println("✓ Resolved: " + name + " (" + baseURI + ")");
                }
            }
            System.out.println("        as: " + source.getSystemId());
            System.out.println("      from: " + ((ResolverInputSource) source).resolvedURI);
        } else {
            if (baseURI == null) {
                System.out.println("✗ Resolved: " + name);
            } else {
                if (name == null) {
                    System.out.println("✗ Resolved: " + baseURI);
                } else {
                    System.out.println("✗ Resolved: " + name + " (" + baseURI + ")");
                }
            }
        }
        return source;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        InputSource source = null;
        if (parent != null) {
            source = ((EntityResolver2) parent).resolveEntity(name, publicId, baseURI, systemId);
        }

        String display = "";
        if (name != null) {
            display += name + ": ";
        }
        if (baseURI == null) {
            if (systemId != null) {
                display += systemId;
            }
        } else {
            display += URI.create(baseURI).resolve(systemId).toString();
        }
        if (publicId != null) {
            display += " (" + publicId + ")";
        }

        if (source instanceof ResolverInputSource) {
            System.out.println("✓ Resolved: " + display);
            System.out.println("        as: " + source.getSystemId());
            System.out.println("      from: " + ((ResolverInputSource) source).resolvedURI);
        } else {
            System.out.println("✗ Resolved: " + display);
        }
        return source;
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        Source source = null;
        if (parent != null) {
            source = ((URIResolver) parent).resolve(href, base);
        }

        String display = "";
        if (href != null) {
            display += href;
        }
        if (base != null) {
            display += " (" + base + ")";
        }

        if (source instanceof ResolverSAXSource) {
            System.out.println("✓ Resolved: " + display);
            System.out.println("        as: " + source.getSystemId());
            System.out.println("      from: " + ((ResolverSAXSource) source).resolvedURI);
        } else {
            System.out.println("✗ Resolved: " + display);
        }
        return source;
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        LSInput source = null;
        if (parent != null) {
            source = ((LSResourceResolver) parent).resolveResource(type, namespaceURI, publicId, systemId, baseURI);
        }

        String display = "";
        if (type != null) {
            display += type + ": ";
        }
        if (baseURI == null) {
            if (systemId != null) {
                display += systemId;
            }
        } else {
            display += URI.create(baseURI).resolve(systemId).toString();
        }
        if (namespaceURI != null) {
            display += " (" + namespaceURI + ")";
        }
        if (publicId != null) {
            display += " (" + publicId + ")";
        }

        if (source instanceof ResolverLSInput) {
            System.out.println("✓ Resolved: " + display);
            System.out.println("        as: " + source.getSystemId());
            System.out.println("      from: " + ((ResolverLSInput) source).resolvedURI);
        } else {
            System.out.println("✗ Resolved: " + display);
        }
        return source;
    }
}
