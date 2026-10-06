// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** XML of WebDAV and CalDAV, read with the DOM parser both Android and the JVM ship. */
internal object DavXml {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val CALENDARSERVER = "http://calendarserver.org/ns/"
    const val APPLE = "http://apple.com/ns/ical/"

    private const val NAMESPACES =
        """xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" """ +
            """xmlns:cs="http://calendarserver.org/ns/" xmlns:a="http://apple.com/ns/ical/""""

    fun propfind(props: String) =
        """<?xml version="1.0" encoding="utf-8"?><d:propfind $NAMESPACES><d:prop>$props</d:prop></d:propfind>"""

    fun syncCollection(token: String?) =
        """<?xml version="1.0" encoding="utf-8"?><d:sync-collection $NAMESPACES>""" +
            "<d:sync-token>${token?.let(
                ::escape
            ).orEmpty()}</d:sync-token><d:sync-level>1</d:sync-level>" +
            "<d:prop><d:getetag/></d:prop></d:sync-collection>"

    fun multiget(hrefs: List<String>) =
        """<?xml version="1.0" encoding="utf-8"?><c:calendar-multiget $NAMESPACES>""" +
            "<d:prop><d:getetag/><c:calendar-data/></d:prop>" +
            hrefs.joinToString("") { "<d:href>${escape(it)}</d:href>" } + "</c:calendar-multiget>"

    fun allEvents() = """<?xml version="1.0" encoding="utf-8"?><c:calendar-query $NAMESPACES>""" +
        "<d:prop><d:getetag/></d:prop><c:filter><c:comp-filter name=\"VCALENDAR\">" +
        "<c:comp-filter name=\"VEVENT\"/></c:comp-filter></c:filter></c:calendar-query>"

    /** The properties a calendar can be created with or changed by; null ones are left out. */
    fun calendarProperties(name: String?, color: String?, order: Int?) = buildString {
        name?.let { append("<d:displayname>${escape(it)}</d:displayname>") }
        color?.let { append("<a:calendar-color>${escape(it)}</a:calendar-color>") }
        order?.let { append("<a:calendar-order>$it</a:calendar-order>") }
    }

    fun mkcalendar(name: String, color: String?) =
        """<?xml version="1.0" encoding="utf-8"?><c:mkcalendar $NAMESPACES><d:set><d:prop>""" +
            calendarProperties(name, color, null) +
            "<c:supported-calendar-component-set><c:comp name=\"VEVENT\"/>" +
            "</c:supported-calendar-component-set>" +
            "</d:prop></d:set></c:mkcalendar>"

    fun proppatch(properties: String) =
        """<?xml version="1.0" encoding="utf-8"?><d:propertyupdate $NAMESPACES>""" +
            "<d:set><d:prop>$properties</d:prop></d:set></d:propertyupdate>"

    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** The responses of a multistatus body, or null when it is not one. */
    fun multistatus(body: String): Multistatus? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // No DOCTYPE, so no external entities: the body comes from the network. Android's
            // parser does not support the feature, but it never resolves external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder().parse(
            InputSource(StringReader(body))
        ).documentElement
        require(root.localName == "multistatus" && root.namespaceURI == DAV)
        val responses = root.children(DAV, "response").map { response ->
            DavResponse(
                href = response.child(DAV, "href")?.textContent?.trim().orEmpty(),
                status = response.child(DAV, "status")?.textContent?.let(::statusCode),
                properties = response.children(DAV, "propstat")
                    .filter { propstat ->
                        propstat.child(DAV, "status")?.textContent?.let(::statusCode) ==
                            HTTP_OK
                    }
                    .flatMap { it.child(DAV, "prop")?.elements().orEmpty() }
            )
        }
        Multistatus(
            responses,
            root.child(DAV, "sync-token")?.textContent?.trim()?.takeIf {
                it.isNotEmpty()
            }
        )
    }.getOrNull()

    private fun statusCode(line: String): Int? = line.trim().split(' ').getOrNull(1)?.toIntOrNull()

    private const val HTTP_OK = 200
}

/** One `<d:response>`: its href, its own status if any, and the properties found (200). */
internal data class DavResponse(val href: String, val status: Int?, val properties: List<Element>) {
    fun property(namespace: String, name: String): Element? =
        properties.firstOrNull { it.namespaceURI == namespace && it.localName == name }

    fun text(namespace: String, name: String): String? =
        property(namespace, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

/** A parsed multistatus body; [syncToken] is set by sync-collection answers. */
internal data class Multistatus(val responses: List<DavResponse>, val syncToken: String?)

internal fun Element.elements(): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }.filter {
        it.nodeType ==
            Node.ELEMENT_NODE
    }
        .map { it as Element }

internal fun Element.children(namespace: String, name: String): List<Element> =
    elements().filter { it.namespaceURI == namespace && it.localName == name }

internal fun Element.child(namespace: String, name: String): Element? =
    children(namespace, name).firstOrNull()
