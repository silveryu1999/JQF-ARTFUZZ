import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;

import org.w3c.dom.Document;

/**
 * @author Rohan Padhye
 */
public class XMLDocumentUtils {
    private static TransformerFactory transformerFactory =
            TransformerFactory.newInstance();

    public static String documentToString(Document document) {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter stream = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stream));
            return stream.toString();
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream documentToInputStream(Document document) {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(stream));
            return new ByteArrayInputStream(stream.toByteArray());
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}