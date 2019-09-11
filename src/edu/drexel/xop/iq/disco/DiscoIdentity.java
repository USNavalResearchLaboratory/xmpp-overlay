/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq.disco;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * Represents disco Identity elements
 *
 * @author David Millar
 */
public class DiscoIdentity {
    private Element element;
    private String category;
    private String type;
    private String name;

    public DiscoIdentity(String category, String type, String name) {
        if (category == null) {
            throw new IllegalArgumentException("Argument 'category' must not be null.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Argument 'type' must not be null.");
        }

        this.category = category;
        this.type = type;
        this.name = name;

        element = DocumentHelper.createElement("identity");
        element.addAttribute("category", category);
        element.addAttribute("type", type);
        if (name != null) {
            element.addAttribute("name", name);
        }
    }

    /**
     * @return the element
     */
    public Element getElement() {
        return element;
    }

    /**
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }


}

/*
 * From http://xmpp.org/schemas/disco-info.xsd
<xs:element name="identity">
    <xs:complexType>
        <xs:simpleContent>
            <xs:extension base="empty">
                <xs:attribute name="category" type="nonEmptyString" use="required"/>
                <xs:attribute name="name" type="xs:string" use="optional"/>
                <xs:attribute name="type" type="nonEmptyString" use="required"/>
            </xs:extension>
        </xs:simpleContent>
    </xs:complexType>
</xs:element>
 */
