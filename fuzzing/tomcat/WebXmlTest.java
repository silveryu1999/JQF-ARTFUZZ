import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.pholser.junit.quickcheck.From;
//import edu.berkeley.cs.jqf.examples.xml.XMLDocumentUtils;
//import edu.berkeley.cs.jqf.examples.xml.XmlDocumentGenerator;
//import edu.berkeley.cs.jqf.examples.common.Dictionary;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@RunWith(JQF.class)
public class WebXmlTest {

    @Fuzz
    public void testWithInputStream(InputStream in) {
        InputSource inputSource = new InputSource(in);
        WebXml webXml = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);
        boolean success = parser.parseWebXml(inputSource, webXml, false);
        Assume.assumeTrue(success);
    }

    @Fuzz
    public void testWithGenerator(@From(XmlDocumentGenerator.class) @Dictionary("dictionaries/tomcat-webxml.dict") Document dom) {
        testWithInputStream(XMLDocumentUtils.documentToInputStream(dom));
    }

    @Fuzz
    public void debugWithGenerator(@From(XmlDocumentGenerator.class) @Dictionary("dictionaries/tomcat-webxml.dict") Document dom) {
        System.out.println(XMLDocumentUtils.documentToString(dom));
        testWithGenerator(dom);
    }

    @Fuzz
    public void testWithString(String input){
        testWithInputStream(new ByteArrayInputStream(input.getBytes()));
    }

    @Test
    public void testSmall() {
        testWithString("<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\" version=\"2.5\">\n" +
                "    <servlet>\n" +
                "        <servlet-name>comingsoon</servlet-name>\n" +
                "        <servlet-class>mysite.server.ComingSoonServlet</servlet-class>\n" +
                "    </servlet>\n" +
                "    <servlet-mapping>\n" +
                "        <servlet-name>comingsoon</servlet-name>\n" +
                "        <url-pattern>/*</url-pattern>\n" +
                "    </servlet-mapping>\n" +
                "</web-app>");
    }
}