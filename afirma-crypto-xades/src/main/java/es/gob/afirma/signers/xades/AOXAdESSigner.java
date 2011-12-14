/*******************************************************************************
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde http://forja-ctt.administracionelectronica.gob.es/
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo  bajo licencia GPL version 2  segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 ******************************************************************************/

package es.gob.afirma.signers.xades;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import net.java.xades.security.xml.XAdES.DataObjectFormat;
import net.java.xades.security.xml.XAdES.DataObjectFormatImpl;
import net.java.xades.security.xml.XAdES.ObjectIdentifierImpl;
import net.java.xades.security.xml.XAdES.SignaturePolicyIdentifier;
import net.java.xades.security.xml.XAdES.SignaturePolicyIdentifierImpl;
import net.java.xades.security.xml.XAdES.SignatureProductionPlace;
import net.java.xades.security.xml.XAdES.SignatureProductionPlaceImpl;
import net.java.xades.security.xml.XAdES.SignerRole;
import net.java.xades.security.xml.XAdES.SignerRoleImpl;
import net.java.xades.security.xml.XAdES.XAdES;
import net.java.xades.security.xml.XAdES.XAdES_EPES;
import net.java.xades.util.Base64;
import net.java.xades.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.AOFormatFileException;
import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.MimeHelper;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AOSignInfo;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.core.signers.AOSigner;
import es.gob.afirma.core.util.tree.AOTreeModel;
import es.gob.afirma.core.util.tree.AOTreeNode;
import es.gob.afirma.signers.xml.Utils;
import es.gob.afirma.signers.xml.Utils.CannotDereferenceException;
import es.gob.afirma.signers.xml.Utils.IsInnerlException;
import es.gob.afirma.signers.xml.Utils.ReferenceIsNotXMLException;
import es.gob.afirma.signers.xml.XMLConstants;

/** Manejador de firmas XML XAdES.
 * <p>Soporta XAdES-BES y XAdES-EPES.</p>
 * <p>
 *  Debido a errores en algunas versiones del entorno de ejecuci&oacute;n de Java, esta clase puede generar ocasionalmente
 *  mensajes en consola del tipo: <code>[Fatal Error] :1:1: Content is not allowed in prolog.</code>. Estos
 *  deben ignorarse, ya que no indican ninguna condici&oacute;n de error ni malfuncionamiento.
 * </p>
 * <p>
 *  Los atributos espec&iacute;ficos XAdES implementados por esta clase (adem&aacute;s de los 
 *  relativos a las pol�ticas de firma) son:
 * </p>
 * <ul>
 *  <li><i>SigningTime</i></li>
 *  <li><i>SigningCerticate</i></li>
 *  <li><i>IssuerSerial</i></li>
 *  <li><i>SignedDataObjectProperties</i></li>
 * </ul>
 * <p><b>Distintos formatos de firmas XML</b></p>
 * <dl>
 *  <dt><i>Detached</i></dt>
 *  <dd>
 *   <p>
 *    La firma XML en modo <i>Detached</i> permite tener una firma de forma separada e independiente del
 *    contenido firmado, pudiendo relacionar firma con contenido firmado mediante una referencia de tipo
 *    URI. Este tipo de firmas es &uacute;til cuando no se puede modificar el contenido original pero se
 *    desea constatar su autenticidad, procedencia, etc.<br>
 *   </p>
 *   <p>
 *    Un uso com&uacute;n de este formato es en la descarga de ficheros, pudiendo poner a disposici&oacute;n
 *    del internauta, junto al contenido a descargar, un peque&ntilde;o fichero de firma para verificar la
 *    integridad del primero.
 *   </p>
 *   <p>
 *    Un ejemplo de este tipo de firmas ser&iacute;a la siguiente estructura (resumida) XML:
 *   </p>
 *   <pre>
 *   &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *    &lt;ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#"&gt;
 *     &lt;ds:SignedInfo&gt;
 *      &lt;ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/&gt;
 *      &lt;ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/&gt;
 *      &lt;ds:Reference URI="http://www.mpt.es/contenido"&gt;
 *       &lt;ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/&gt;
 *       &lt;ds:DigestValue/&gt;
 *      &lt;/ds:Reference&gt;
 *     &lt;/ds:SignedInfo&gt;
 *     &lt;ds:SignatureValue/&gt;
 *    &lt;/ds:Signature&gt;
 *   </pre>
 *   <p>
 *    En este ejemplo, los datos firmados se encuentran en un servidor Web accesible p&uacute;blicamente:
 *    <cite>http://www.mpt.es/contenido</cite>, y se referencia como tal, conformando lo que se denomina
 *    <i>Externally Detached</i> o "Detached Externa".
 *   </p>
 *   <p>
 *    Cuando se desea firmar un contenido con un formato <i>Detached</i>, pero se quiere eliminar la
 *    dependencia de la disponibilidad externa del contenido firmado, es posible crear una estructura XML
 *    que contenga los propios contenidos y la firma, pero cada uno en una subestructura independiente, 
 *    manteniendo as� el concepto de <i>Detached</i> (firma y contenido firmado no se interrelacionan 
 *    directamente).  Para adecuarse al est&aacute;ndar los nodos de firma y contenido debe encontrarse en el 
 *    mismo nivel dentro del XML.
 *   </p>
 *   <p>
 *    Un ejemplo de esta estructura XML ser&iacute;a:
 *   </p>
 *   <pre>
 *    &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *    &lt;internally-detached&gt;
 *     &lt;ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#"&gt;
 *      &lt;ds:SignedInfo&gt;
 *       &lt;ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/&gt;
 *       &lt;ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/&gt;
 *       &lt;ds:Reference URI="#data"&gt;
 *         &lt;ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/&gt;
 *         &lt;ds:DigestValue/&gt;
 *       &lt;/ds:Reference&gt;
 *      &lt;/ds:SignedInfo&gt;
 *      &lt;ds:SignatureValue/&gt;
 *     &lt;/ds:Signature&gt;
 *     &lt;document Id="data"&gt;
 *      &lt;title&gt;title&lt;/title&gt;
 *      &lt;author&gt;writer&lt;/author&gt;
 *      &lt;date&gt;today&lt;/date&gt;
 *      &lt;content&gt;
 *       &lt;para&gt;First paragraph&lt;/para&gt;
 *       &lt;para&gt;Second paragraph&lt;/para&gt;
 *      &lt;/content&gt;
 *     &lt;/document&gt;
 *    &lt;/internally-detached&gt;
 *   </pre>
 *   <p>
 *    En este caso, la estructura <i>internally-detached</i> contiene dos subestructuras, la firma (<i>Signature</i>) y el propio
 *    contenido firmado (<i>document</i>). La forma de relacionar ambos es, como ocurr&iacute;a en el primer ejemplo, con una URI,
 *    solo que en este caso es interna al documento XML, referenciando el identificador de la subestructura del contenido firmado
 *    (<i>data</i>).
 *   </p>
 *   <p>
 *    A esta variante de firma <i>Detached</i> se la conoce como <i>Internally Detached</i>, o "Detached Interna".
 *   </p>
 *   <p>
 *    Para unificar las superestructuras creadas dentro de un formato "Detached Interno", el Cliente @firma 
 *    construye siempre el siguiente esqueleto XML:
 *   </p>
 *   <pre>
 *    &lt;CONTENT Id="id" Encoding="codificacion" MimeType="MimeType" Algorithm="�"&gt;
 *     &lt;! � CONTENIDO FIRMADO --&gt;
 *    &lt;/CONTENT&gt;
 *   </pre>
 *   <p>
 *    Es decir, el contenido a firmar, ya sea XML o no-XML, se encapsula dentro de una etiqueta XML llamada
 *    CONTENT, en la que se indica la codificaci&oacute;n del contenido (UTF-8, Base64, etc.), el tipo de 
 *    contenido (imagen JPEG, texto, XML, etc.) y el algoritmo utilizado para calcular la huella digital de 
 *    este (por ejemplo, SHA-1).
 *   </p>
 *   <p>
 *    Como la superestructura es XML, si el contenido tambi&eacute;n es XML la inserci&oacute;n es directa 
 *    (como en el primer ejemplo de "Detached Interna", pero si no es XML se codifica en Base64 antes de 
 *    insertarse, resultando una estructura con una forma similar a la siguiente:
 *   </p>
 *   <pre>
 *    &lt;CONTENT Id="id" Encoding="Base64" MimeType="application/octect-stream" Algorithm="�"&gt;
 *     SFGJKASGFJKASEGUYFGEYGEYRGADFJKASGDFSUYFGAUYEGWEYJGDFYKGYKGWJKEGYFWYJ=
 *    &lt;/CONTENT&gt;
 *   </pre>
 *   <p>
 *    La larga cadena de caracteres ser&iacute;a una codificaci&oacute;n Base64 del original interpretado en su 
 *    forma binaria pura.
 *   </p>
 *  </dd>
 *  <dt><i>Enveloping</i></dt>
 *  <dd>
 *   <p>
 *    Otra variante de firma es la <i>Enveloping</i>, en la que la estructura XML de firma es la &uacute;nica 
 *    en el documento de firma, y esta contiene internamente el contenido firmado (en un nodo propio).
 *   </p>
 *   <pre>
 *    &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *    &lt;ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#"&gt;
 *     &lt;ds:SignedInfo&gt;
 *      &lt;ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/&gt;
 *      &lt;ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/&gt;
 *      &lt;ds:Reference URI="#obj"&gt;
 *       &lt;ds:DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/&gt;
 *       &lt;ds:DigestValue/&gt;
 *      &lt;/ds:Reference&gt;
 *     &lt;/ds:SignedInfo&gt;
 *     &lt;ds:SignatureValue/&gt;
 *     &lt;ds:Object Id="obj"&gt;SFGJKASGFJKASEGUYFGEYGEYRGADFJKASGDFSUYFG=&lt;/ds:Object&gt;
 *    &lt;/ds:Signature&gt;
 *   </pre>
 *   <p>
 *    En este caso, los datos firmados se encuentran en el nodo <i>Object</i>, referenciados 
 *    internamente al XML mediante el identificador <i>obj</i>.
 *   </p>
 *   <p>
 *    Al igual que ocurr&iacute;a con el formato <i>Detached</i>, si los datos no son XML, no 
 *    es posible insertarlos directamente dentro de una estructura XML, por lo que se codifican 
 *    previamente en Base64.
 *   </p>
 *  </dd>
 *  <dt><i>Enveloped</i></dt>
 *  <dd>
 *   <p>
 *    Este formato de firma XMLDSig est&aacute; pensado para que un contenido XML pueda auto-contener 
 *    su propia firma digital, insert&aacute;ndola en un nodo propio interno, por lo que, al contrario 
 *    que en los formatos anteriores, no es posible firmar contenido que no sea XML.
 *   </p>
 *   <p>
 *    Un ejemplo simple del resultado de una firma <i>Enveloped</i> podr&iacute;a ser el siguiente:
 *   </p>
 *   <pre>
 *    &lt;!DOCTYPE Enveloped [
 *     &lt;!ENTITY ds "http://www.w3.org/2000/09/xmldsig#"&gt;
 *     &lt;!ENTITY c14n "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"&gt;
 *     &lt;!ENTITY enveloped "http://www.w3.org/2000/09/xmldsig#enveloped-signature"&gt;
 *     &lt;!ENTITY xslt "http://www.w3.org/TR/1999/REC-xslt-19991116"&gt;
 *     &lt;!ENTITY digest "http://www.w3.org/2000/09/xmldsig#sha1"&gt;
 *    ]&gt;
 *    &lt;Letter&gt;
 *     &lt;Return-address&gt;address&lt;/Return-address&gt;
 *     &lt;To&gt;You&lt;/To&gt;
 *     &lt;Message&gt;msg body&lt;/Message&gt;
 *     &lt;From&gt;
 *      &lt;ds:Signature xmlns:ds="&ds;"&gt;
 *       &lt;ds:SignedInfo&gt;
 *        &lt;ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/&gt;
 *        &lt;ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/&gt;
 *        &lt;ds:Reference URI=""&gt;
 *         &lt;ds:Transforms&gt;
 *          &lt;ds:Transform Algorithm="&enveloped;"&gt;&lt;/ds:Transform&gt;
 *         &lt;/ds:Transforms&gt;
 *         &lt;ds:DigestMethod Algorithm="&digest;"/&gt;
 *         &lt;ds:DigestValue&gt;&lt;/ds:DigestValue&gt;
 *        &lt;/ds:Reference&gt;
 *       &lt;/ds:SignedInfo&gt;
 *       &lt;ds:SignatureValue/&gt;
 *      &lt;/ds:Signature&gt;
 *     &lt;/From&gt;
 *     &lt;Attach&gt;attachement&lt;/Attach&gt;
 *    &lt;/Letter&gt;
 *   </pre>
 *   <p>
 *    En este caso, el documento original (<i>Letter</i>), contiene internamente la estructura de firma digital
 *    (<i>Signature</i>).
 *   </p>
 *   <p>
 *    Una peculiaridad de la estructura generada es que esta referenciada mediante una URI vac&iacute;a 
 *    (<code>URI=""</code>), lo cual indica que la firma aplica a la totalidad del documento original.
 *   </p>
 *  </dd>
 * @version 0.3 */
public final class AOXAdESSigner implements AOSigner {
    
    static final Logger LOGGER = Logger.getLogger("es.agob.afirma"); //$NON-NLS-1$
    
    private static final String SIGNATURE_TAG = "Signature"; //$NON-NLS-1$

    /** URI que define la versi&oacute;n por defecto de XAdES. */
    private static final String XADESNS = "http://uri.etsi.org/01903/v1.3.2#"; //$NON-NLS-1$

    /** URI que define una referencia de tipo OBJECT. */
    private static final String OBJURI = "http://www.w3.org/2000/09/xmldsig#Object"; //$NON-NLS-1$

    private static final String CSURI = "http://uri.etsi.org/01903#CountersignedSignature"; //$NON-NLS-1$
    private static final String AFIRMA = "AFIRMA"; //$NON-NLS-1$
    private static final String XML_SIGNATURE_PREFIX = "ds"; //$NON-NLS-1$
    private static final String XADES_SIGNATURE_PREFIX = "xades"; //$NON-NLS-1$
    private static final String SIGNATURE_NODE_NAME = XML_SIGNATURE_PREFIX + ":Signature"; //$NON-NLS-1$
    private static final String DETACHED_CONTENT_ELEMENT_NAME = "CONTENT"; //$NON-NLS-1$
    private static final String DETACHED_STYLE_ELEMENT_NAME = "STYLE"; //$NON-NLS-1$

    /** Algoritmo de huella digital por defecto para las referencias XML. */
    private static final String DIGEST_METHOD = DigestMethod.SHA1;
    
    private static final String HTTP_PROTOCOL_PREFIX = "http://"; //$NON-NLS-1$
    private static final String HTTPS_PROTOCOL_PREFIX = "https://"; //$NON-NLS-1$
    
    private static final String STYLE_REFERENCE_PREFIX = "StyleReference-"; //$NON-NLS-1$
    
    private static final String MIMETYPE_STR = "MimeType"; //$NON-NLS-1$
    private static final String ENCODING_STR = "Encoding"; //$NON-NLS-1$

    private String algo;
    private Document doc;

    static {
        if (Security.getProvider("XMLDSig") == null) { //$NON-NLS-1$
            try {
                Security.addProvider(new org.jcp.xml.dsig.internal.dom.XMLDSigRI());
            }
            catch (final Exception e) {
                LOGGER.warning("No se ha podido agregar el proveedor de firma XMLDSig necesario para firmas XML: " + e); //$NON-NLS-1$
            }
        }
    }

    /** Firma datos en formato XAdES.
     * <p>
     * Este m&eacute;todo, al firmar un XML, firmas tambi&eacute;n sus hojas de estilo XSL asociadas, siguiendo el siguiente criterio:
     * <ul>
     *  <li>Firmas XML <i>Enveloped</i></li>
     *  <ul>
     *   <li>Hoja de estilo con ruta relativa</li>
     *  <ul>
     *   <li>No se firma.</li>
     *  </ul>
     *   <li>Hola de estilo remota con ruta absoluta</li>
     *  <ul>
     *   <li>Se restaura la declaraci&oacute;n de hoja de estilo tal y como estaba en el XML original</li>
     *   <li>Se firma una referencia (<i>canonicalizada</i>) a esta hoja remota</li>
     *  </ul>
     *   <li>Hoja de estilo empotrada</li>
     *   <ul>
     *    <li>Se restaura la declaraci&oacute;n de hoja de estilo tal y como estaba en el XML original</li>
     *   </ul>
     *  </ul>
     *  <li>Firmas XML <i>Externally Detached</i></li>
     *  <ul>
     *   <li>Hoja de estilo con ruta relativa</li>
     *   <ul>
     *    <li>No se firma.</li>
     *   </ul>
     *   <li>Hola de estilo remota con ruta absoluta</li>
     *   <ul>
     *    <li>Se firma una referencia (<i>canonicalizada</i>) a esta hoja remota</li>
     *   </ul>
     *   <li>Hoja de estilo empotrada</li>
     *   <ul>
     *    <li>No es necesaria ninguna acci&oacute;n</li>
     *   </ul>
     *  </ul>
     *  <li>Firmas XML <i>Enveloping</i></li>
     *  <ul>
     *   <li>Hoja de estilo con ruta relativa</li>
     *   <ul>
     *    <li>No se firma.</li>
     *   </ul>
     *   <li>Hola de estilo remota con ruta absoluta</li>
     *   <ul>
     *    <li>Se firma una referencia (<i>canonicalizada</i>) a esta hoja remota</li>
     *   </ul>
     *   <li>Hoja de estilo empotrada</li>
     *   <ul>
     *    <li>No es necesaria ninguna acci&oacute;n</li>
     *   </ul>
     *  </ul>
     *  <li>Firmas XML <i>Internally Detached</i></li>
     *  <ul>
     *   <li>Hoja de estilo con ruta relativa</li>
     *   <ul>
     *    <li>No se firma.</li>
     *   </ul>
     *   <li>Hola de estilo remota con ruta absoluta</li>
     *   <ul>
     *    <li>Se firma una referencia (<i>canonicalizada</i>) a esta hoja remota</li>
     *   </ul>
     *   <li>Hoja de estilo empotrada</li>
     *   <ul>
     *    <li>No es necesaria ninguna acci&oacute;n</li>
     *   </ul>
     *  </ul> 
     * </ul>
     * </p>
     * @param data Datos que deseamos firmar.
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param keyEntry Entrada que apunta a la clave privada a usar para firmar.
     * @param xParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>xParams</code>:</p>
     * <dl>
     *  <dt><b><i>uri</i></b></dt>
     *   <dd>URL en la que se encuentra el documento a firmar, necesario en el caso del formato <i>XAdES Externally Detached</i></dd>
     *  <dt><b><i>format</i></b></dt>
     *   <dd>
     *    Formato de firma. Se aceptan los siguientes valores:<br>
     *    <ul>
     *     <li>
     *      <i>XAdES Detached</i> (<code>AOSignConstants.SIGN_FORMAT_XADES_DETACHED</code>)
     *     </li>
     *     <li>
     *      <i>XAdES Externally Detached</i> (<code>AOSignConstants.SIGN_FORMAT_XADES_EXTERNALLY_DETACHED</code>)
     *      <p>
     *       Para el uso del formato <i>XAdES Externally Detached</i> es necesario establecer 
     *       tambi&eacute;n el par&aacute;metro <code>uri</code> con una direcci&oacute;n
     *       accesible universalmente.
     *      </p>
     *     </li>
     *     <li>
     *      <i>XAdES Enveloped</i> (<code>AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED</code>)
     *     </li>
     *     <li>
     *      <i>XAdES Enveloping</i> (<code>AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING</code>)
     *     </li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>Identificador de la pol&iacute;tica de firma (normalmente una URL hacia la pol&iacute;tica en formato XML procesable)</dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normlamente del mismo fichero en formato XML procesable).
     *    Si no se indica, es obligatorio que el par&aacute;metro <code>policyIdentifier</code> sea una URL accesible universalmente 
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>
     *  <dt><b><i>policyDescription</i></b></dt>
     *   <dd>Descripci&oacute;n textual de la pol&iacute;tica</dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>URL hacia el documento (legible por personas, normalmente en formato PDF) descriptivo de la pol&iacute;tica de firma</dd>
     *  <dt><b><i>signerClaimedRole</i></b></dt>
     *   <dd>Cargo atribuido para el firmante</dd>
     *  <dt><b><i>signerCertifiedRole</i></b></dt>
     *   <dd>Cargo confirmado para el firmante</dd>
     *  <dt><b><i>precalculatedHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo de huella digital cuando esta se proporciona precalculada</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionProvince</i></b></dt>
     *   <dd>Provincia en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionPostalCode</i></b></dt>
     *   <dd>C&oacute;digo postal en el que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionCountry</i></b></dt>
     *   <dd>Pa&iacute;s en el que se realiza la firma</dd>
     *  <dt><b><i>xmlTransforms</i></b></dt>
     *   <dd>N&uacute;mero de transformaciones a aplicar al XML antes de firmarlo</dd>
     *  <dt><b><i>xmlTransform</i>n<i>Type</i></b></dt>
     *   <dd>Tipo de la transformaci&oacute;n <i>n</i> (debe ser la URL del algoritmo segun define W3C)</dd>
     *  <dt><b><i>xmlTransform</i>n<i>Subtype</i></b></dt>
     *   <dd>Subtipo de la transformaci&oacute;n <i>n</i> (por ejemplo, "intersect", "subtract" o "union" para XPATH2)</dd>
     *  <dt><b><i>xmlTransform</i>n<i>Body</i></b></dt>
     *   <dd>Cuerpo de la transformaci&oacute;n <i>n</i></dd>
     *  <dt><b><i>referencesDigestMethod</i></b></dt>
     *   <dd>
     *    Algoritmo de huella digital a usar en las referencias XML (referencesDigestMethod). Debe indicarse como una URL, 
     *    acept&aacute;ndose los siguientes valores:
     *    <ul>
     *     <li><i>http://www.w3.org/2000/09/xmldsig#sha1</i> (SHA-1)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha256</i> (SHA-256, valor recomendado)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha512</i> (SHA-512)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#ripemd160 (RIPEMD-160)</i></li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>mimeType</i></b></dt>
     *   <dd>
     *    MIME-Type de los datos a firmar. Si no se indica se realiza una auto-detecci&oacute;n cuyo resultado puede
     *    ser inexacto
     *   </dd>
     *  <dt><b><i>encoding</i></b></dt>
     *   <dd>
     *    Codificaci&oacute;n de los datos a firmar
     *   </dd>
     *  <dt><b><i>oid</i></b><dt>
     *   <dd>OID que identifica el tipo de datos a firmar</dd>
     *  <dt><b><i>canonicalizationAlgorithm</i></b></dt>
     *   <dd>Algoritmo de canonicalizaci&oacute;n</dd>
     *  <dt><b><i>xadesNamespace</i></b></dt>
     *   <dd>URL de definici&oacute;n del espacio de nombres de XAdES (y por extensi&oacute;n, versi&oacute;n de XAdES)</dd> <!--
     *  <dt><b><i>xmlDSigNamespacePrefix</i></b></dt>
     *   <dd>Prefijo a usar en el espacio de nombres de XMLDSig (normalmente "dsig" o "ds")</dd> -->
     *  <dt><b><i>ignoreStyleSheets</i></b></dt>
     *   <dd>
     *    Ignora las hojas de estilo externas de los XML (no las firma) si se establece a <code>true</code>, 
     *    si se establece a <code>false</code> act&uacute;a normalmente (s&iacute; las firma)
     *   </dd>
     *  <dt><b><i>avoidBase64Transforms</i></b></dt>
     *   <dd>
     *    No declara transformaciones Base64 incluso si son necesarias si se establece a <code>true</code>, 
     *    si se establece a <code>false</code> act&uacute;a normalmente (s&iacute; las declara)
     *   </dd>
     *  <dt><b><i>headLess</i></b></dt>
     *   <dd>
     *    Evita cualquier interacci&oacute;n con el usuario si se establece a <code>true</code>, 
     *    si se establece a <code>false</code> act&uacute;a normalmente (puede mostrar di&aacute;logos, 
     *    por ejemplo, para la dereferenciaci&oacute;n de hojas de estilo enlazadas con rutas relativas).
     *    &Uacute;til para los procesos desatendidos y por lotes
     *   </dd>
     *  <dt><b><i>applySystemDate</i></b></dt>
     *   <dd>
     *    Indica si se debe introducir en la firma el atributo <i>signingTime</i> con la fecha actual
     *    del sistema. Por defecto, se encuentra a {@code true}. 
     *   </dd>
     * </dl>
     * <p>
     *  Respecto al uso de los par&aacute;metros <code>xmlTransform</code>n<code>Type</code>, 
     *  <code>xmlTransform</code>n<code>Subtype</code> y <code>xmlTransform</code>n<code>Body</code>,
     *  sus valores van ligados, acept&aacute;ndose las siguientes combinaciones:
     * </p>
     * <p>
     *  Transformaci&oacute;n <b>XPATH</b><br>
     *  &nbsp;&nbsp;-<b>Tipo</b>: <code>http://www.w3.org/TR/1999/REC-xpath-19991116</code><br>
     *  &nbsp;&nbsp;-<b>Subtipos</b>: No tiene subtipos.<br>
     *  &nbsp;&nbsp;-<b>Cuerpo</b>: Especificado mediante sentencias de tipo XPATH.<br>
     *  <br>Transformaci&oacute;n <b>XPATH2</b><br>
     *  &nbsp;&nbsp;-<b>Tipo</b>: <code>http://www.w3.org/2002/06/xmldsig-filter2</code><br>
     *  &nbsp;&nbsp;-<b>Subtipos</b>:<br>
     *  &nbsp;&nbsp;&nbsp;&nbsp;<b><i>subtract</i></b>: Resta.<br>
     *  &nbsp;&nbsp;&nbsp;&nbsp;<b><i>intersect</i></b>: Intersecci&oacute;n<br>
     *  &nbsp;&nbsp;&nbsp;&nbsp;<b><i>union</i></b>: Uni&oacute;n<br>
     *  &nbsp;&nbsp;-<b>Cuerpo</b>: Especificado mediante sentencias de tipo XPATH2.<br>
     *  <br>Transformaci&oacute;n <b>XSLT</b><br>
     *  &nbsp;&nbsp;-<b>Tipo</b>: <code>http://www.w3.org/TR/1999/REC-xslt-19991116</code><br>
     *  &nbsp;&nbsp;-<b>Subtipos</b>: No tiene subtipos.<br>
     *  &nbsp;&nbsp;-<b>Cuerpo</b>: Especificado mediante sentencias de tipo XSLT.<br>
     *  <br>Transformaci&oacute;n <b>BASE64</b><br>
     *  &nbsp;&nbsp;-<b>Tipo</b>: <code>http://www.w3.org/2000/09/xmldsig#base64</code><br>
     *  &nbsp;&nbsp;-<b>Subtipos</b>: No tiene subtipos.<br>
     *  &nbsp;&nbsp;-<b>Cuerpo</b>: No tiene cuerpo.
     * </p>
     * <p>
     *  No es posible especificar transformaciones complejas que incluyan varias sentencias. 
     *  En su lugar, puede declararse una sucesi&oacute;n de transformaciones simples que produzcan el 
     *  mismo resultado. Cada una de las transformaciones se aplicar&aacute; de forma ordenada sobre el 
     *  resultado de la anterior.
     * </p>
     * @return Firma en formato XAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso */
    public byte[] sign(final byte[] data, 
                       final String algorithm, 
                       final PrivateKeyEntry keyEntry, 
                       final Properties xParams) throws AOException {

        final String algoUri = XMLConstants.SIGN_ALGOS_URI.get(algorithm);
        if (algoUri == null) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + algorithm + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        final Properties extraParams = (xParams != null) ? xParams : new Properties();

        final String format = extraParams.getProperty("format", AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING); //$NON-NLS-1$
        final String mode = extraParams.getProperty("mode", AOSignConstants.SIGN_MODE_IMPLICIT); //$NON-NLS-1$
        final String digestMethodAlgorithm = extraParams.getProperty("referencesDigestMethod", DIGEST_METHOD); //$NON-NLS-1$
        final String canonicalizationAlgorithm = extraParams.getProperty("canonicalizationAlgorithm", CanonicalizationMethod.INCLUSIVE); //$NON-NLS-1$
        final String xadesNamespace = extraParams.getProperty("xadesNamespace", XADESNS); //$NON-NLS-1$
        final boolean ignoreStyleSheets = Boolean.parseBoolean(extraParams.getProperty("ignoreStyleSheets", Boolean.TRUE.toString())); //$NON-NLS-1$ 
        final boolean avoidBase64Transforms = Boolean.parseBoolean(extraParams.getProperty("avoidBase64Transforms", Boolean.FALSE.toString())); //$NON-NLS-1$
        final boolean headLess = Boolean.parseBoolean(extraParams.getProperty("headLess", Boolean.TRUE.toString())); //$NON-NLS-1$ 
        final String precalculatedHashAlgorithm = extraParams.getProperty("precalculatedHashAlgorithm"); //$NON-NLS-1$
        String mimeType = extraParams.getProperty("mimeType"); //$NON-NLS-1$
        String encoding = extraParams.getProperty("encoding"); //$NON-NLS-1$
        if ("base64".equalsIgnoreCase(encoding)) { //$NON-NLS-1$
            encoding = XMLConstants.BASE64_ENCODING;
        }
        String oid = extraParams.getProperty("oid"); //$NON-NLS-1$
        final ObjectIdentifierImpl objectIdentifier = (oid != null) ? new ObjectIdentifierImpl("OIDAsURN", (oid.startsWith("urn:oid:") ? "" : "urn:oid:") + oid, null, new ArrayList<String>(0)) : null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        URI uri = null;
        try {
            uri = new URI(extraParams.getProperty("uri")); //$NON-NLS-1$
        }
        catch (final Exception e) {
            // Ignoramos errores, el parametro es opcional
        }

        Utils.checkIllegalParams(format, mode, uri, precalculatedHashAlgorithm, true);

        // Un externally detached con URL permite los datos nulos o vacios
        if ((data == null || data.length == 0) && !(format.equals(AOSignConstants.SIGN_FORMAT_XADES_EXTERNALLY_DETACHED) && uri != null)) {
            throw new AOException("No se han podido leer los datos a firmar"); //$NON-NLS-1$
        }

        // Propiedades del documento XML original
        final Map<String, String> originalXMLProperties = new Hashtable<String, String>();

        // carga el documento xml
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // Elemento de datos
        Element dataElement;

        final String contentId = DETACHED_CONTENT_ELEMENT_NAME + "-" + UUID.randomUUID().toString(); //$NON-NLS-1$
        final String styleId = DETACHED_STYLE_ELEMENT_NAME + "-" + UUID.randomUUID().toString(); //$NON-NLS-1$
        boolean isBase64 = false;
        boolean wasEncodedToBase64 = false;

        // Elemento de estilo
        Element styleElement = null;
        String styleType = null;
        String styleHref = null;
        String styleEncoding = null;

        if (mode.equals(AOSignConstants.SIGN_MODE_IMPLICIT)) {
            try {

                // Obtenemos el objeto XML
                final Document docum = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(data));

                // Obtenemos la hoja de estilo del XML
                try {
                    final Properties p;
                    if (!ignoreStyleSheets) {
                        p = Utils.getStyleSheetHeader(new String(data));
                    }
                    else {
                        p = new Properties();
                    }
                    styleType = p.getProperty("type"); //$NON-NLS-1$
                    styleHref = p.getProperty("href"); //$NON-NLS-1$

                    if (styleType != null && styleHref != null) {

                        LOGGER.info("Se ha encontrado una hoja de estilo asociada al XML a firmar: tipo=" + styleType //$NON-NLS-1$
                                                               + ", referencia=" //$NON-NLS-1$
                                                               + styleHref);

                        try {
                            final Document tmpDoc =
                                    Utils.dereferenceStyleSheet(
                                        TransformerFactory.newInstance().getAssociatedStylesheet(
                                             new StreamSource(new ByteArrayInputStream(data)),
                                             null,
                                             null,
                                             null
                                        ).getSystemId(),
                                        headLess
                                    );

                            // Cuidado!! Solo rellenamos el Elemento DOM si no
                            // es HTTP o HTTPS, porque si es accesible
                            // remotamente no necesito el elemento, ya que se
                            // firma via referencia Externally Detached
                            if (!styleHref.startsWith(HTTP_PROTOCOL_PREFIX) && !styleHref.startsWith(HTTPS_PROTOCOL_PREFIX)) {
                                styleElement = tmpDoc.getDocumentElement();
                            }

                            styleEncoding = tmpDoc.getXmlEncoding();
                        }
                        catch (final IsInnerlException ex) {
                            LOGGER.info("La hoja de estilo esta referenciada internamente, por lo que no se necesita dereferenciar"); //$NON-NLS-1$
                        }
                        catch (final ReferenceIsNotXMLException ex) {
                            LOGGER.warning("La hoja de estilo referenciada no es XML o no se ha dereferenciado apropiadamente"); //$NON-NLS-1$
                        }
                        catch (final CannotDereferenceException ex) {
                            LOGGER.warning("La hoja de estilo no ha podido dereferenciar, probablemente sea un enlace relativo local"); //$NON-NLS-1$
                        }
                        catch (final Exception ex) {
                            LOGGER.severe("Error intentando dereferenciar la hoja de estilo: " + ex); //$NON-NLS-1$
                        }
                    }
                }
                catch (final Exception e) {
                    LOGGER.info("No se ha encontrado ninguna hoja de estilo asociada al XML a firmar"); //$NON-NLS-1$
                }

                // Si no hay asignado un MimeType o es el por defecto
                // establecemos el de XML
                if (mimeType == null || XMLConstants.DEFAULT_MIMETYPE.equals(mimeType)) {
                    mimeType = "text/xml"; //$NON-NLS-1$
                }

                // Obtenemos el encoding del documento original
                if (encoding == null) {
                    encoding = docum.getXmlEncoding();
                }

                // Hacemos la comprobacion del base64 por si se establecido
                // desde fuera
                if (encoding != null && !XMLConstants.BASE64_ENCODING.equals(encoding)) {
                    originalXMLProperties.put(OutputKeys.ENCODING, encoding);
                }

                String tmpXmlProp = docum.getXmlVersion();
                if (tmpXmlProp != null) {
                    originalXMLProperties.put(OutputKeys.VERSION, tmpXmlProp);
                }
                final DocumentType dt = docum.getDoctype();
                if (dt != null) {
                    tmpXmlProp = dt.getSystemId();
                    if (tmpXmlProp != null) {
                        originalXMLProperties.put(OutputKeys.DOCTYPE_SYSTEM, tmpXmlProp);
                    }
                }

                if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_DETACHED)) {
                    dataElement = docum.createElement(DETACHED_CONTENT_ELEMENT_NAME);
                    dataElement.setAttributeNS(null, "Id", contentId); //$NON-NLS-1$
                    dataElement.setAttributeNS(null, MIMETYPE_STR, mimeType); 
                    if (encoding != null && (!"".equals(encoding))) { //$NON-NLS-1$
                        dataElement.setAttributeNS(null, ENCODING_STR, encoding); 
                    }
                    dataElement.appendChild(docum.getDocumentElement());

                    // Tambien el estilo
                    if (styleElement != null) {
                        try {
                            final Element tmpStyleElement = docum.createElement(DETACHED_STYLE_ELEMENT_NAME);
                            tmpStyleElement.setAttributeNS(null, "Id", styleId); //$NON-NLS-1$
                            if (styleType != null) {
                                tmpStyleElement.setAttributeNS(null, MIMETYPE_STR, styleType); 
                            }
                            tmpStyleElement.setAttributeNS(null, ENCODING_STR, styleEncoding); 

                            tmpStyleElement.appendChild(docum.adoptNode(styleElement.cloneNode(true)));

                            styleElement = tmpStyleElement;
                        }
                        catch (final Exception e) {
                            LOGGER.warning("No ha sido posible crear el elemento DOM para incluir la hoja de estilo del XML como Internally Detached: " + e); //$NON-NLS-1$
                            styleElement = null;
                        }
                    }
                }
                else {
                    dataElement = docum.getDocumentElement();
                }

            }
            // captura de error en caso de no ser un documento xml
            catch (final Exception e) {
                if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED)) {
                    throw new AOFormatFileException("El modo Enveloped solo permite firmar datos XML", e); //$NON-NLS-1$
                }
                // para los formatos de firma internally detached y enveloping
                // se trata de convertir el documento a base64
                LOGGER.info("El documento no es un XML valido. Se convertira a Base64: " + e); //$NON-NLS-1$

                try {
                    // crea un nuevo nodo xml para contener los datos en base 64
                    final Document docFile = dbf.newDocumentBuilder().newDocument();
                    dataElement = docFile.createElement(DETACHED_CONTENT_ELEMENT_NAME);
                    uri = null;
                    if (mimeType == null) {
                        mimeType = XMLConstants.DEFAULT_MIMETYPE;
                    }

                    dataElement.setAttributeNS(null, "Id", contentId); //$NON-NLS-1$

                    // Si es base 64, lo firmamos indicando como contenido el
                    // dato pero, ya que puede
                    // poseer un formato particular o caracteres valido pero
                    // extranos para el XML,
                    // realizamos una decodificacion y recodificacion para asi
                    // homogenizar el formato.
                    if (AOUtil.isBase64(data) && (XMLConstants.BASE64_ENCODING.equals(encoding) || ((encoding != null) ? encoding : "").toLowerCase().equals("base64"))) { //$NON-NLS-1$ //$NON-NLS-2$
                        LOGGER.info("El documento se ha indicado como Base64, se insertara como tal en el XML"); //$NON-NLS-1$

                        // Adicionalmente, si es un base 64 intentamos obtener
                        // el tipo del contenido
                        // decodificado para asi reestablecer el MimeType.
                        final byte[] decodedData = Base64.decode(data);
                        final MimeHelper mimeTypeHelper = new MimeHelper(decodedData);
                        final String tempMimeType = mimeTypeHelper.getMimeType();
                        mimeType = tempMimeType != null ? tempMimeType : XMLConstants.DEFAULT_MIMETYPE;
                        dataElement.setAttributeNS(null, MIMETYPE_STR, mimeType); 
                        dataElement.setTextContent(Base64.encodeBytes(decodedData));
                    }
                    else {
                        if (XMLConstants.BASE64_ENCODING.equals(encoding)) {
                            LOGGER.info("El documento se ha indicado como Base64, pero no es un Base64 valido. Se convertira a Base64 antes de insertarlo en el XML y se declarara la transformacion"); //$NON-NLS-1$
                        }
                        else {
                            LOGGER.info("El documento se considera binario, se convertira a Base64 antes de insertarlo en el XML y se declarara la transformacion"); //$NON-NLS-1$
                        }
                        // Usamos el MimeType identificado
                        dataElement.setAttributeNS(null, MIMETYPE_STR, mimeType); 
                        dataElement.setTextContent(Base64.encodeBytes(data));
                        wasEncodedToBase64 = true;
                    }
                    isBase64 = true;
                    encoding = XMLConstants.BASE64_ENCODING;
                    dataElement.setAttributeNS(null, ENCODING_STR, encoding); 
                }
                catch (final Exception ex) {
                    throw new AOException("Error al convertir los datos a base64", ex); //$NON-NLS-1$
                }
            }
        }

        // Firma Explicita
        else {

            // ESTE BLOQUE CONTIENE EL PROCESO A SEGUIR EN EL MODO EXPLICITO,
            // ESTO ES, NO FIRMAMOS LOS DATOS SINO SU HASH
            byte[] digestValue = null;
            // Si la URI no es nula recogemos los datos de fuera
            if (uri != null) {

                final byte[] tmpData;
                try {
                    tmpData = AOUtil.getDataFromInputStream(AOUtil.loadFile(uri));
                }
                catch (final Exception e) {
                    throw new AOException("No se han podido obtener los datos de la URI externa '" + uri + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
                }
                // Vemos si hemos obtenido bien los datos de la URI
                if (tmpData != null && tmpData.length > 0) {
                    try {
                        digestValue = MessageDigest.getInstance("SHA1").digest(tmpData); //$NON-NLS-1$
                    }
                    catch (final Exception e) {
                        throw new AOException("No se ha podido obtener el SHA1 de los datos de la URI externa", e); //$NON-NLS-1$
                    }
                }
            }
            // Si se nos ha introducido el messageDigest, firmamos este como si
            // fuesen los datos
            else if (precalculatedHashAlgorithm != null) {
                digestValue = data;
            }
            // El hash de los datos, ni una URI a traves de la que calcularlos,
            // entonces lo calculamos
            // a traves de los datos introducidos (Siempre se calcula el SHA-1
            // de los datos)
            else {
                try {
                    digestValue = MessageDigest.getInstance("SHA1").digest(data); //$NON-NLS-1$
                }
                catch (final Exception e) {
                    throw new AOException("No se ha podido obtener el SHA1 de los datos proporcionados", e); //$NON-NLS-1$
                }
            }

            if (digestValue == null || digestValue.length < 1) {
                throw new AOException("Error al obtener la huella SHA1 de los datos"); //$NON-NLS-1$
            }

            final Document docFile;
            try {
                docFile = dbf.newDocumentBuilder().newDocument();
            }
            catch (final Exception e) {
                throw new AOException("No se ha podido crear el documento XML contenedor", e); //$NON-NLS-1$
            }
            dataElement = docFile.createElement(DETACHED_CONTENT_ELEMENT_NAME);

            encoding = XMLConstants.BASE64_ENCODING;
            // En el caso de la firma explicita, se firma el Hash de los datos
            // en lugar de los propios datos.
            // En este caso, los indicaremos a traves del MimeType en donde
            // establecemos un tipo especial
            // que designa al hash. Independientemente del algoritmo de firma
            // utilizado, el Hash de las firmas
            // explicitas de datos siempre sera SHA1, salvo que el hash se haya
            // establecido desde fuera.
            String hashAlgoUri;
            if (precalculatedHashAlgorithm != null) {
                mimeType = "hash/" + precalculatedHashAlgorithm.toLowerCase(); //$NON-NLS-1$
                hashAlgoUri = XMLConstants.MESSAGEDIGEST_ALGOS_URI.get(precalculatedHashAlgorithm.toLowerCase());
            }
            else {
                mimeType = "hash/sha1"; //$NON-NLS-1$
                hashAlgoUri = XMLConstants.MESSAGEDIGEST_ALGOS_URI.get("sha1"); //$NON-NLS-1$
            }

            dataElement.setAttributeNS(null, "Id", contentId); //$NON-NLS-1$
            dataElement.setAttributeNS(null, MIMETYPE_STR, mimeType); 
            dataElement.setAttributeNS(null, ENCODING_STR, encoding); 
            if (hashAlgoUri != null) {
                dataElement.setAttributeNS(null, "hashAlgorithm", hashAlgoUri); //$NON-NLS-1$
            }
            dataElement.setTextContent(Base64.encodeBytes(digestValue));
            isBase64 = true;

            // FIN BLOQUE EXPLICITO
        }

        // ***************************************************
        // ***************************************************

        final String tmpUri = "#" + contentId; //$NON-NLS-1$
        final String tmpStyleUri = "#" + styleId; //$NON-NLS-1$

        // Crea el nuevo documento de firma
        Document docSignature = null;
        try {
            docSignature = dbf.newDocumentBuilder().newDocument();
            if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED)) {
                docSignature.appendChild(docSignature.adoptNode(dataElement));
            }
            else {
                docSignature.appendChild(docSignature.createElement(AFIRMA));
            }
        }
        catch (final Exception e) {
            throw new AOException("Error al crear la firma en formato " + format + ", modo " + mode, e); //$NON-NLS-1$ //$NON-NLS-2$
        }

        final List<Reference> referenceList = new ArrayList<Reference>();
        final XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM"); //$NON-NLS-1$
        final DigestMethod digestMethod;
        try {
            digestMethod = fac.newDigestMethod(digestMethodAlgorithm, null);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido obtener un generador de huellas digitales para el algoritmo '" + digestMethodAlgorithm + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        final String referenceId = "Reference-" + UUID.randomUUID().toString(); //$NON-NLS-1$
        final String referenceStyleId = STYLE_REFERENCE_PREFIX + UUID.randomUUID().toString(); 

        final List<Transform> transformList = new ArrayList<Transform>();

        // Primero anadimos las transformaciones a medida
        Utils.addCustomTransforms(transformList, extraParams, XML_SIGNATURE_PREFIX);

        // Solo canonicalizo si es XML
        if (!isBase64) {
            try {
                // Transformada para la canonicalizacion inclusiva
                transformList.add(fac.newTransform(canonicalizationAlgorithm, (TransformParameterSpec) null));
            }
            catch (final Exception e) {
                LOGGER.severe("No se puede encontrar el algoritmo de canonicalizacion, la referencia no se canonicalizara: " + e); //$NON-NLS-1$
            }
        }
        // Si no era XML y tuve que convertir a Base64 yo mismo declaro la
        // transformacion
        else if (wasEncodedToBase64 && !avoidBase64Transforms) {
            try {
                transformList.add(fac.newTransform(Transform.BASE64, (TransformParameterSpec) null));
            }
            catch (final Exception e) {
                LOGGER.severe("No se puede encontrar el algoritmo transformacion Base64, esta no se declarara: " + e); //$NON-NLS-1$
            }
        }

        // crea una referencia al documento insertado en un nodo Object para la
        // firma enveloping y a el estilo
        XMLObject envelopingObject = null;
        XMLObject envelopingStyleObject = null;

        if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING)) {
            try {
                // crea el nuevo elemento Object que contiene el documento a
                // firmar
                final List<XMLStructure> structures = new ArrayList<XMLStructure>(1);

                // Si los datos se han convertido a base64, bien por ser
                // binarios o explicitos
                if (isBase64) {
                    structures.add(new DOMStructure(dataElement.getFirstChild()));
                }
                else {
                    structures.add(new DOMStructure(dataElement));
                }

                final String objectId = "Object-" + UUID.randomUUID().toString(); //$NON-NLS-1$
                envelopingObject = fac.newXMLObject(structures, objectId, mimeType, encoding);

                // crea la referencia al nuevo elemento Object
                referenceList.add(fac.newReference("#" + objectId, digestMethod, transformList, OBJURI, referenceId)); //$NON-NLS-1$

                // Vamos con la hoja de estilo
                if (styleElement != null) {
                    final String objectStyleId = "StyleObject-" + UUID.randomUUID().toString(); //$NON-NLS-1$
                    envelopingStyleObject =
                            fac.newXMLObject(Collections.singletonList(new DOMStructure(styleElement)), objectStyleId, styleType, styleEncoding);
                    referenceList.add(fac.newReference("#" + objectStyleId, //$NON-NLS-1$
                                                       digestMethod,
                                                       Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                  (TransformParameterSpec) null)),
                                                       OBJURI,
                                                       referenceStyleId));

                }
            }
            catch (final Exception e) {
                throw new AOException("Error al generar la firma en formato enveloping", e); //$NON-NLS-1$
            }

            // Hojas de estilo para enveloping en Externally Detached
            if (styleHref != null && styleElement == null && (styleHref.startsWith(HTTP_PROTOCOL_PREFIX) || styleHref.startsWith(HTTPS_PROTOCOL_PREFIX))) { // Comprobamos si la referencia al estilo es externa
                try {
                    referenceList.add(fac.newReference(styleHref,
                                                       digestMethod,
                                                       Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                  (TransformParameterSpec) null)),
                                                       null,
                                                       referenceStyleId));
                }
                catch (final Exception e) {
                    LOGGER.severe("No ha sido posible anadir la referencia a la hoja de estilo del XML, esta no se firmara: " + e); //$NON-NLS-1$
                }
            }

        }

        // crea una referencia al documento mediante la URI hacia el
        // identificador del nodo CONTENT
        else if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_DETACHED)) {
            try {
                if (dataElement != null) {
                    // inserta en el nuevo documento de firma el documento a
                    // firmar
                    docSignature.getDocumentElement().appendChild(docSignature.adoptNode(dataElement));
                    // crea la referencia a los datos firmados que se
                    // encontraran en el mismo documento
                    referenceList.add(fac.newReference(tmpUri, digestMethod, transformList, null, referenceId));
                }
                if (styleElement != null) {
                    // inserta en el nuevo documento de firma la hoja de estilo
                    docSignature.getDocumentElement().appendChild(docSignature.adoptNode(styleElement));
                    // crea la referencia a los datos firmados que se
                    // encontraran en el mismo documento
                    referenceList.add(fac.newReference(tmpStyleUri,
                                                       digestMethod,
                                                       Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                  (TransformParameterSpec) null)),
                                                       null,
                                                       referenceStyleId));
                }

            }
            catch (final Exception e) {
                throw new AOException("Error al generar la firma en formato detached implicito", e); //$NON-NLS-1$
            }

            // Hojas de estilo remotas para detached
            if (styleHref != null && styleElement == null && (styleHref.startsWith(HTTP_PROTOCOL_PREFIX) || styleHref.startsWith(HTTPS_PROTOCOL_PREFIX))) {  // Comprobamos si la referencia al estilo es externa
                try {
                    referenceList.add(fac.newReference(styleHref,
                                                       digestMethod,
                                                       Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                  (TransformParameterSpec) null)),
                                                       null,
                                                       referenceStyleId));
                }
                catch (final Exception e) {
                    LOGGER.severe("No ha sido posible anadir la referencia a la hoja de estilo del XML, esta no se firmara: " + e); //$NON-NLS-1$
                }
            }

        }

        // Crea una referencia al documento mediante la URI externa si la
        // tenemos o usando un Message Digest
        // precalculado si no tenemos otro remedio
        else if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_EXTERNALLY_DETACHED)) {
            Reference ref = null;
            // No tenemos uri, suponemos que los datos son el message digest
            if (precalculatedHashAlgorithm != null && (uri == null || uri.getScheme().equals("") || uri.getScheme().equals("file"))) { //$NON-NLS-1$ //$NON-NLS-2$
                DigestMethod dm = null;
                try {
                    // Convertimos el algo del Message Digest externo a la
                    // nomenclatura XML
                    if (AOSignConstants.getDigestAlgorithmName(precalculatedHashAlgorithm).equalsIgnoreCase("SHA1")) { //$NON-NLS-1$
                        dm = fac.newDigestMethod(DigestMethod.SHA1, null);
                    }
                    else if (AOSignConstants.getDigestAlgorithmName(precalculatedHashAlgorithm).equalsIgnoreCase("SHA-256")) { //$NON-NLS-1$
                        dm = fac.newDigestMethod(DigestMethod.SHA256, null);
                    }
                    else if (AOSignConstants.getDigestAlgorithmName(precalculatedHashAlgorithm).equalsIgnoreCase("SHA-512")) { //$NON-NLS-1$
                        dm = fac.newDigestMethod(DigestMethod.SHA512, null);
                    }
                    else if (AOSignConstants.getDigestAlgorithmName(precalculatedHashAlgorithm).equalsIgnoreCase("RIPEMD160")) { //$NON-NLS-1$
                        dm = fac.newDigestMethod(DigestMethod.RIPEMD160, null);
                    }
                }
                catch (final Exception e) {
                    throw new AOException("No se ha podido crear el metodo de huella digital para la referencia Externally Detached", e); //$NON-NLS-1$
                }
                if (dm == null) {
                    throw new AOException("Metodo de Message Digest para la referencia Externally Detached no soportado: " + precalculatedHashAlgorithm); //$NON-NLS-1$
                }
                ref = fac.newReference("", dm, null, null, referenceId, data); //$NON-NLS-1$
            }
            // Tenemos URI y no nos han establecido algoritmo de message digest,
            // por lo que es una referencia externa accesible
            else {
                // Si es una referencia de tipo file:// obtenemos el fichero y
                // creamos una referencia solo con
                // el message digest
                if (uri != null && uri.getScheme().equals("file")) { //$NON-NLS-1$
                    try {
                        ref =
                                fac.newReference("", //$NON-NLS-1$
                                                 digestMethod,
                                                 null,
                                                 null,
                                                 referenceId,
                                                 MessageDigest.getInstance(AOSignConstants.getDigestAlgorithmName(digestMethodAlgorithm))
                                                              .digest(AOUtil.getDataFromInputStream(AOUtil.loadFile(uri))));
                    }
                    catch (final Exception e) {
                        throw new AOException("No se ha podido crear la referencia XML a partir de la URI local (" + uri.toASCIIString() + ")", e); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                // Si es una referencia distinta de file:// suponemos que es
                // dereferenciable de forma universal
                // por lo que dejamos que Java lo haga todo
                else if (uri != null) {
                    try {
                        ref = fac.newReference(uri.toASCIIString(), digestMethod);
                    }
                    catch (final Exception e) {
                        throw new AOException("No se ha podido crear la referencia Externally Detached, probablemente por no obtenerse el metodo de digest", //$NON-NLS-1$
                                              e);
                    }
                }
            }
            if (ref == null) {
                throw new AOException("Error al generar la firma Externally Detached, no se ha podido crear la referencia externa"); //$NON-NLS-1$
            }
            referenceList.add(ref);

            // Hojas de estilo remotas en Externally Detached
            if (styleHref != null && styleElement == null) {
                // Comprobamos que la URL es valida
                if (styleHref.startsWith(HTTP_PROTOCOL_PREFIX) || styleHref.startsWith(HTTPS_PROTOCOL_PREFIX)) {  
                    try {
                        referenceList.add(fac.newReference(styleHref,
                                                           digestMethod,
                                                           Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                      (TransformParameterSpec) null)),
                                                           null,
                                                           referenceStyleId));
                    }
                    catch (final Exception e) {
                        LOGGER.severe("No ha sido posible anadir la referencia a la hoja de estilo del XML, esta no se firmara: " + e); //$NON-NLS-1$
                    }
                }
                else {
                    LOGGER.warning("Se necesita una referencia externa HTTP o HTTPS a la hoja de estilo para referenciarla en firmas XML Externally Detached"); //$NON-NLS-1$
                }
            }

        }

        // crea una referencia indicando que se trata de una firma enveloped
        else if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED)) {
            try {

                // Transformacion enveloped
                // La enveloped siempre la primera, para que no se quede sin
                // nodos Signature por haber
                // ejecutado antes otra transformacion
                transformList.add(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null));

                // Transformacion XPATH para eliminar el resto de firmas del
                // documento
                transformList.add(fac.newTransform(Transform.XPATH,
                                                   new XPathFilterParameterSpec("not(ancestor-or-self::" + XML_SIGNATURE_PREFIX + ":Signature)", //$NON-NLS-1$ //$NON-NLS-2$
                                                                                Collections.singletonMap(XML_SIGNATURE_PREFIX, XMLSignature.XMLNS))));

                // crea la referencia
                referenceList.add(fac.newReference("", digestMethod, transformList, null, referenceId)); //$NON-NLS-1$
            }
            catch (final Exception e) {
                throw new AOException("Error al generar la firma en formato enveloped", e); //$NON-NLS-1$
            }

            // Hojas de estilo remotas para enveloped
            if (styleHref != null && styleElement == null && (styleHref.startsWith(HTTP_PROTOCOL_PREFIX) || styleHref.startsWith(HTTPS_PROTOCOL_PREFIX))) { // Comprobamos si la referencia al estilo es externa
                try {
                    referenceList.add(fac.newReference(styleHref,
                                                       digestMethod,
                                                       Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                                  (TransformParameterSpec) null)),
                                                       null,
                                                       referenceStyleId));
                }
                catch (final Exception e) {
                    LOGGER.severe("No ha sido posible anadir la referencia a la hoja de estilo del XML, esta no se firmara: " + e); //$NON-NLS-1$
                }
            }

        }

        // Instancia XADES_EPES
        final XAdES_EPES xades = (XAdES_EPES) XAdES.newInstance(XAdES.EPES, // XAdES
                                                          xadesNamespace, // XAdES NameSpace
                                                          XADES_SIGNATURE_PREFIX, // XAdES Prefix
                                                          XML_SIGNATURE_PREFIX, // XMLDSig Prefix
                                                          digestMethodAlgorithm, // DigestMethod
                                                          docSignature.getDocumentElement() // Element
        );

        // SigningCertificate
        xades.setSigningCertificate((X509Certificate) keyEntry.getCertificate());

        // SignaturePolicyIdentifier
        final SignaturePolicyIdentifier spi =
                getPolicy(extraParams.getProperty("policyIdentifier"), //$NON-NLS-1$
                          extraParams.getProperty("policyIdentifierHash"), //$NON-NLS-1$
                          extraParams.getProperty("policyIdentifierHashAlgorithm"), //$NON-NLS-1$
                          extraParams.getProperty("policyDescription"), //$NON-NLS-1$
                          extraParams.getProperty("policyQualifier")); //$NON-NLS-1$
        if (spi != null) {
            xades.setSignaturePolicyIdentifier(spi);
        }

        // SignatureProductionPlace
        final SignatureProductionPlace spp =
                getSignatureProductionPlace(extraParams.getProperty("signatureProductionCity"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionProvince"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionPostalCode"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionCountry")); //$NON-NLS-1$
        if (spp != null) {
            xades.setSignatureProductionPlace(spp);
        }

        // SignerRole
        SignerRole signerRole = null;
        try {
            final String claimedRole = extraParams.getProperty("signerClaimedRole"); //$NON-NLS-1$
            final String certifiedRole = extraParams.getProperty("signerCertifiedRole"); //$NON-NLS-1$
            signerRole = new SignerRoleImpl();
            if (claimedRole != null) {
                signerRole.addClaimedRole(claimedRole);
            }
            if (certifiedRole != null) {
                signerRole.addCertifiedRole(certifiedRole);
            }
        }
        catch (final Exception e) {
            // Se ignoran los errores, el parametro es opcional
        }
        if (signerRole != null) {
            xades.setSignerRole(signerRole);
        }

        // SigningTime
        if (Boolean.parseBoolean(extraParams.getProperty("applySystemDate", Boolean.TRUE.toString()))) { //$NON-NLS-1$ 
            xades.setSigningTime(new Date());
        }

        // DataObjectFormat
        final ArrayList<DataObjectFormat> objectFormats = new ArrayList<DataObjectFormat>();
        final DataObjectFormat objectFormat = new DataObjectFormatImpl(
             null,
             objectIdentifier,
             mimeType,
             encoding,
             "#" + referenceId //$NON-NLS-1$
        );
        objectFormats.add(objectFormat);
        xades.setDataObjectFormats(objectFormats);

        final AOXMLAdvancedSignature xmlSignature;
        try {
            xmlSignature = AOXMLAdvancedSignature.newInstance(xades);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido instanciar la firma XML Avanzada de JXAdES", e); //$NON-NLS-1$
        }
        try {
            xmlSignature.setDigestMethod(digestMethodAlgorithm);
            xmlSignature.setCanonicalizationMethod(canonicalizationAlgorithm);
        }
        catch (final Exception e) {
            LOGGER.severe("No se ha podido establecer el algoritmo de huella digital (" + algoUri //$NON-NLS-1$
                                                     + "), es posible que el usado en la firma difiera del indicado: " //$NON-NLS-1$
                                                     + e);
        }

        // en el caso de formato enveloping se inserta el elemento Object con el
        // documento a firmar
        if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING)) {
            xmlSignature.addXMLObject(envelopingObject);
            if (envelopingStyleObject != null) {
                xmlSignature.addXMLObject(envelopingStyleObject);
            }
        }

        // Si es enveloped hay que anadir la hoja de estilo dentro de la firma y
        // referenciarla
        if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED) && styleElement != null) {
            xmlSignature.addStyleSheetEnvelopingOntoSignature(styleElement, styleType, styleEncoding, styleId);

            try {
                referenceList.add(fac.newReference(tmpStyleUri,
                                                   digestMethod,
                                                   Collections.singletonList(fac.newTransform(canonicalizationAlgorithm,
                                                                                              (TransformParameterSpec) null)),
                                                   null,
                                                   referenceStyleId));
            }
            catch (final Exception e) {
                LOGGER.severe("No se ha podido anadir una referencia a la hoja de estilo, esta se incluira dentro de la firma, pero no estara firmada: " + e); //$NON-NLS-1$
            }
        }

        // Cadena de certificados
        Certificate[] rawcerts = keyEntry.getCertificateChain();
        List<X509Certificate> certificates = new ArrayList<X509Certificate>(rawcerts.length);
        for (Certificate c : rawcerts) {
            if (c instanceof X509Certificate) {
                certificates.add((X509Certificate)c);
            }
        }
        
        // Genera la firma
        try {
            xmlSignature.sign(certificates, keyEntry.getPrivateKey(), algoUri, referenceList, "Signature-" + UUID.randomUUID().toString(), null /* TSA */); //$NON-NLS-1$
        }
        catch (final NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + algorithm + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (final Exception e) {
            throw new AOException("Error al generar la firma XAdES", e); //$NON-NLS-1$
        }

        // Si se esta realizando una firma enveloping simple no tiene sentido el
        // nodo raiz,
        // asi que sacamos el nodo de firma a un documento aparte
        if (format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING)) {
            try {
                if (docSignature.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG).getLength() == 1) {
                    final Document newdoc = dbf.newDocumentBuilder().newDocument();
                    newdoc.appendChild(newdoc.adoptNode(docSignature.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG).item(0)));
                    docSignature = newdoc;
                }
            }
            catch (final Exception e) {
                LOGGER.info("No se ha eliminado el nodo padre '<AFIRMA>': " + e); //$NON-NLS-1$
            }
        }

        // Si no es enveloped quito los valores para que no se inserte la
        // cabecera de hoja de estilo
        if (!format.equals(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED)) {
            styleHref = null;
            styleType = null;
        }

        return Utils.writeXML(docSignature.getDocumentElement(), originalXMLProperties, styleHref, styleType);

    }

    /** Comprueba si la firma es detached
     * @param element
     *        Elemento que contiene el nodo ra&iacute;z del documento que se
     *        quiere comprobar
     * @return Valor booleano, siendo verdadero cuando la firma es detached */
    public boolean isDetached(final Element element) {
        if (element == null) {
            return false;
        }
        if (element.getFirstChild().getLocalName() != null && element.getFirstChild().getLocalName().equals(DETACHED_CONTENT_ELEMENT_NAME)) {
            return true;
        }
        return false;
    }

    /** Comprueba si la firma es enveloped
     * @param element
     *        Elemento que contiene el nodo ra&iacute;z del documento que se
     *        quiere comprobar
     * @return Valor booleano, siendo verdadero cuando la firma es enveloped */
    public boolean isEnveloped(final Element element) {
        final NodeList transformList = element.getElementsByTagNameNS(XMLConstants.DSIGNNS, "Transform"); //$NON-NLS-1$
        for (int i = 0; i < transformList.getLength(); i++) {
            if (((Element) transformList.item(i)).getAttribute("Algorithm").equals(Transform.ENVELOPED)) { //$NON-NLS-1$
                return true;
            }
        }
        return false;
    }

    /** Comprueba si la firma es enveloping
     * @param element
     *        Elemento que contiene el nodo ra&iacute;z del documento que se
     *        quiere comprobar
     * @return Valor booleano, siendo verdadero cuando la firma es enveloping */
    public boolean isEnveloping(final Element element) {
        if (element.getLocalName().equals(SIGNATURE_TAG) || 
           (element.getLocalName().equals(AFIRMA) && element.getFirstChild().getLocalName().equals(SIGNATURE_TAG))) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    public byte[] getData(final byte[] sign) throws AOInvalidFormatException {
        // nueva instancia de DocumentBuilderFactory que permita espacio de
        // nombres (necesario para XML)
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        final Element rootSig;
        Element elementRes = null;
        try {

            // comprueba que sea una documento de firma valido
            if (!isSign(sign)) {
                throw new AOInvalidFormatException("El documento no es un documento de firmas valido."); //$NON-NLS-1$
            }

            // obtiene la raiz del documento de firmas
            rootSig = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign)).getDocumentElement();

            // si es detached
            if (this.isDetached(rootSig)) {

                final Element firstChild = (Element) rootSig.getFirstChild();
                // si el documento es un xml se extrae como tal
                if (firstChild.getAttribute(MIMETYPE_STR).equals("text/xml")) { //$NON-NLS-1$ 
                    elementRes = (Element) firstChild.getFirstChild();
                }
                // Si el MimeType es de tipo Hash (tipo creado para el cliente
                // afirma) asi que la firma no tiene datos
                // else if
                // (firstChild.getAttribute(MIMETYPE_STR).startsWith("hash/")) {
                // elementRes = null;
                // }
                // si el documento es binario se deshace la codificacion en
                // Base64 si y solo si esta declarada esta transformacion 
                else {
                	//TODO: Deshacer solo el Base64 si existe la transformacion Base64 (COMPROBAR)
                	return isBase64TransformationDeclared(rootSig, firstChild.getAttribute("Id")) ? //$NON-NLS-1$
                				Base64.decode(firstChild.getTextContent()) :
                					firstChild.getTextContent().getBytes();
                }
            }

            // si es enveloped
            else if (this.isEnveloped(rootSig)) {

                // TODO: Revisar si es conveniente eliminar las firmas a traves
                // de transformadas

                // obtiene las firmas y las elimina
                final NodeList signatures = rootSig.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
                for (int i = 0; i < signatures.getLength(); i++) {
                    rootSig.removeChild(signatures.item(0));
                }

                elementRes = rootSig;
            }

            // si es enveloping
            else if (this.isEnveloping(rootSig)) {

                // obtiene el nodo Object de la primera firma
                final Element object = (Element) rootSig.getElementsByTagNameNS(XMLConstants.DSIGNNS, "Object").item(0); //$NON-NLS-1$
                // si el documento es un xml se extrae como tal
                if (object.getAttribute(MIMETYPE_STR).equals("text/xml")) { //$NON-NLS-1$ 
                    elementRes = (Element) object.getFirstChild();
                }
                // si el documento es binario se deshace la codificacion en
                // Base64 si y solo si esta declarada esta transformacion 
                else {
                	//TODO: Deshacer solo el Base64 si existe la transformacion Base64 (COMPROBAR)
                	return isBase64TransformationDeclared(rootSig, object.getAttribute("Id")) ? //$NON-NLS-1$
                				Base64.decode(object.getTextContent()) :
                					object.getTextContent().getBytes();
                }
            }
        }
        catch (final Exception ex) {
            throw new AOInvalidFormatException("Error al leer el fichero de firmas", ex); //$NON-NLS-1$
        }

        // si no se ha recuperado ningun dato se devuelve null
        if (elementRes == null) {
            return null;
        }

        // convierte el documento obtenido en un array de bytes
        final ByteArrayOutputStream baosSig = new ByteArrayOutputStream();
        XMLUtils.writeXML(baosSig, elementRes, false);

        return baosSig.toByteArray();
    }

    /**
     * Comprueba si unos datos firmados tienen declarados una transformaci&oacute;n de tipo Base64.
     * @param rootSig Nodo raiz de la firma.
     * @param objectId Identificador de los datos.
     * @return Devuelve {@code true} si la transformaci&oacute;n est&aacute; definida, {@code false}
     * en caso contrario.
     */
    private boolean isBase64TransformationDeclared(Element rootSig, String objectId) {
    	if (objectId == null || objectId.trim().equals("")) { //$NON-NLS-1$
    		return false;
    	}
    	
    	Element reference = null;
		NodeList references = rootSig.getElementsByTagNameNS(XMLConstants.DSIGNNS, "Reference"); //$NON-NLS-1$
		for (int i = 0; i < references.getLength(); i++) {
			reference = (Element) references.item(i);
			if (reference.hasAttribute("URI") && ("#" + objectId).equals(reference.getAttribute("URI"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				break;
			}
			reference = null;
		}
		if (reference != null) {
			NodeList transforms = reference.getElementsByTagNameNS(XMLConstants.DSIGNNS, "Transform"); //$NON-NLS-1$
			for (int i = 0; i < transforms.getLength(); i++) {
				if (((Element) transforms.item(i)).hasAttribute("Algorithm") && //$NON-NLS-1$
						XMLConstants.BASE64_ENCODING.equals(((Element) transforms.item(i)).getAttribute("Algorithm"))) { //$NON-NLS-1$
					return true;
				}
			}
		}
		return false;
    }
    
    private SignatureProductionPlace getSignatureProductionPlace(final String city,
                                                                 final String province,
                                                                 final String postalCode,
                                                                 final String country) {
        if (city == null && province == null && postalCode == null && country == null) {
            return null;
        }
        return new SignatureProductionPlaceImpl(city, province, postalCode, country);
    }

    private SignaturePolicyIdentifier getPolicy(final String identifier,
                                                final String identifierHash,
                                                final String identifierHashAlgorithm,
                                                final String description, 
                                                final String qualifier) {
        if (identifier == null) {
            return null;
        }
        
        String hashAlgo = null;
        if (identifierHashAlgorithm != null) {
            String normalDigAlgo = null;
            try {
                normalDigAlgo = AOSignConstants.getDigestAlgorithmName(identifierHashAlgorithm);
            }
            catch(final Exception e) {
                LOGGER.warning("El algoritmo de huella digital para el identificador de politica de firma no es valido, se intentara dereferenciar la politica y se aplicara SHA1: " + e); //$NON-NLS-1$
            }
            if ("SHA1".equals(normalDigAlgo)) { //$NON-NLS-1$
                hashAlgo = DigestMethod.SHA1;
            }
            else if ("SHA-256".equals(normalDigAlgo)) { //$NON-NLS-1$
                hashAlgo = DigestMethod.SHA256;
            }
            else if ("SHA-512".equals(normalDigAlgo)) { //$NON-NLS-1$
                hashAlgo = DigestMethod.SHA512;
            }
            else if ("RIPEMD160".equals(normalDigAlgo)) { //$NON-NLS-1$
                hashAlgo = DigestMethod.RIPEMD160;
            }
        }
        final SignaturePolicyIdentifier spi = new SignaturePolicyIdentifierImpl(false);
        try {
            spi.setIdentifier(identifier, (hashAlgo != null) ? identifierHash : null, hashAlgo);
        }
        catch (final Exception e) {
            LOGGER.warning("No se ha podido acceder al identificador ('" + identifier //$NON-NLS-1$
                                                      + "') de la politica " //$NON-NLS-1$
                                                      + "de firma, no se anadira este campo"); //$NON-NLS-1$
            return null;
        }
        // FIXME: Error en JXAdES. Si la descripcion es nula toda la firma
        // falla.
        final String desc = (description != null ? description : ""); //$NON-NLS-1$
        spi.setDescription(desc);

        if (qualifier != null) {
            spi.setQualifier(qualifier);
        }
        return spi;
    }

    /** Cofirma datos en formato XAdES.
     * <p>
     *  Este m&eacute;todo firma todas las referencias a datos declaradas en la firma original,
     *  ya apunten estas a datos, hojas de estilo o cualquier otro elemento. En cada referencia
     *  firmada se introduciran las mismas transformaciones que existiesen en la firma original.
     * </p>
     * <p>
     *  A nivel de formato interno, cuando cofirmamos un documento ya firmado previamente, esta 
     *  firma previa no se modifica. Si tenemos en cuenta que XAdES es en realidad un subconjunto 
     *  de XMLDSig, el resultado de una cofirma XAdES sobre un documento firmado previamente con 
     *  XMLDSig (o viceversa), son dos firmas independientes, una en XAdES y otra en XMLDSig.<br>
     *  Dado que todas las firmas XAdES son XMLDSig pero no todas las firmas XMLDSig son XAdES, 
     *  el resultado global de la firma se adec&uacute;a al est�ndar m�s amplio, XMLDSig en este caso.
     * </p>
     * @param data Datos que deseamos firmar.
     * @param sign Documento con las firmas iniciales. 
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param keyEntry Entrada que apunta a la clave privada a usar para firmar.
     * @param xParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>xParams</code>:</p>
     * <dl>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>Identificador de la pol&iacute;tica de firma (normalmente una URL hacia la pol&iacute;tica en formato XML procesable)</dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normlamente del mismo fichero en formato XML procesable).
     *    Si no se indica, es obligatorio que el par&aacute;metro <code>policyIdentifier</code> sea una URL accesible universalmente 
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>
     *  <dt><b><i>policyDescription</i></b></dt>
     *   <dd>Descripci&oacute;n textual de la pol&iacute;tica</dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>URL hacia el documento (legible por personas, normalmente en formato PDF) descriptivo de la pol&iacute;tica de firma</dd>
     *  <dt><b><i>signerClaimedRole</i></b></dt>
     *   <dd>Cargo atribuido para el firmante</dd>
     *  <dt><b><i>signerCertifiedRole</i></b></dt>
     *   <dd>Cargo confirmado para el firmante</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionProvince</i></b></dt>
     *   <dd>Provincia en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionPostalCode</i></b></dt>
     *   <dd>C&oacute;digo postal en el que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionCountry</i></b></dt>
     *   <dd>Pa&iacute;s en el que se realiza la firma</dd>
     *  <dt><b><i>referencesDigestMethod</i></b></dt>
     *   <dd>
     *    Algoritmo de huella digital a usar en las referencias XML (referencesDigestMethod). Debe indicarse como una URL, 
     *    acept&aacute;ndose los siguientes valores:
     *    <ul>
     *     <li><i>http://www.w3.org/2000/09/xmldsig#sha1</i> (SHA-1)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha256</i> (SHA-256, valor recomendado)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha512</i> (SHA-512)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#ripemd160 (RIPEMD-160)</i></li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>canonicalizationAlgorithm</i></b></dt>
     *   <dd>Algoritmo de canonicalizaci&oacute;n</dd>
     *  <dt><b><i>xadesNamespace</i></b></dt>
     *   <dd>URL de definici&oacute;n del espacio de nombres de XAdES (y por extensi&oacute;n, versi&oacute;n de XAdES)</dd>
     *   <dt><b><i>applySystemDate</i></b></dt>
     *   <dd>
     *    Indica si se debe introducir en la firma el atributo <i>signingTime</i> con la fecha actual
     *    del sistema. Por defecto, se encuentra a {@code true}. 
     *   </dd>
     * </dl>
     * @return Cofirma en formato XAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso */
    public byte[] cosign(final byte[] data, 
                         final byte[] sign, 
                         final String algorithm, 
                         final PrivateKeyEntry keyEntry, 
                         final Properties xParams) throws AOException {

        final String algoUri = XMLConstants.SIGN_ALGOS_URI.get(algorithm);
        if (algoUri == null) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + algorithm + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        final Properties extraParams = (xParams != null) ? xParams: new Properties();

        final String digestMethodAlgorithm = extraParams.getProperty("referencesDigestMethod", DIGEST_METHOD); //$NON-NLS-1$
        final String canonicalizationAlgorithm = extraParams.getProperty("canonicalizationAlgorithm", CanonicalizationMethod.INCLUSIVE); //$NON-NLS-1$
        final String xadesNamespace = extraParams.getProperty("xadesNamespace", XADESNS); //$NON-NLS-1$

        // nueva instancia de DocumentBuilderFactory que permita espacio de
        // nombres (necesario para XML)
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // Propiedades del documento XML original
        final Map<String, String> originalXMLProperties = new Hashtable<String, String>();

        // carga el documento XML de firmas y su raiz
        Document docSig;
        Element rootSig;
        try {
            docSig = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign));
            rootSig = docSig.getDocumentElement();

            // Si el documento contiene una firma simple se inserta como raiz el
            // nodo AFIRMA
            if (rootSig.getNodeName().equals(SIGNATURE_NODE_NAME)) {
                docSig = insertarNodoAfirma(docSig);
                rootSig = docSig.getDocumentElement();
            }
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido leer el documento XML de firmas", e); //$NON-NLS-1$
        }

        final List<Reference> referenceList = new ArrayList<Reference>();
        final XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM"); //$NON-NLS-1$
        final DigestMethod digestMethod;
        try {
            digestMethod = fac.newDigestMethod(digestMethodAlgorithm, null);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido obtener un generador de huellas digitales para el algoritmo '" + digestMethodAlgorithm + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Localizamos la primera firma (primer nodo "Signature") en profundidad
        // en el arbol de firma.
        // Se considera que todos los objetos "Signature" del documento firman
        // (referencian) los mismos
        // objetos, por lo que podemos extraerlos de cualquiera de las firmas
        // actuales.
        // Buscamos dentro de ese Signature todas las referencias que apunten a
        // datos para firmarlas
        final List<String> referencesIds = new ArrayList<String>();
        Node currentElement;
        final NodeList nl = ((Element) docSig.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG).item(0)).getElementsByTagNameNS(XMLConstants.DSIGNNS, "Reference"); //$NON-NLS-1$

        // Se considera que la primera referencia de la firma son los datos que
        // debemos firmar, ademas
        // de varias referencias especiales
        for (int i = 0; i < nl.getLength(); i++) {
            currentElement = nl.item(i);

            // Firmamos la primera referencia (que seran los datos firmados) y
            // las hojas de estilo que
            // tenga asignadas. Las hojas de estilo tendran un identificador que
            // comience por STYLE_REFERENCE_PREFIX.
            // TODO: Identificar las hojas de estilo de un modo generico.
            final NamedNodeMap currentNodeAttributes = currentElement.getAttributes();
            if (i == 0 || (currentNodeAttributes.getNamedItem("Id") != null && currentNodeAttributes.getNamedItem("Id")  //$NON-NLS-1$//$NON-NLS-2$
                                                                                                    .getNodeValue()
                                                                                                    .startsWith(STYLE_REFERENCE_PREFIX))) { 

                // Buscamos las transformaciones declaradas en la Referencia,
                // para anadirlas
                // tambien en la nueva
                final List<Transform> currentTransformList;
                try {
                    currentTransformList = Utils.getObjectReferenceTransforms(currentElement, XML_SIGNATURE_PREFIX);
                }
                catch (final NoSuchAlgorithmException e) {
                    throw new AOException("Se ha declarado una transformacion personalizada de un tipo no soportado", e); //$NON-NLS-1$
                }
                catch (final InvalidAlgorithmParameterException e) {
                    throw new AOException("Se han especificado parametros erroneos para una transformacion personalizada", e); //$NON-NLS-1$
                }

                // Creamos un identificador de referencia para el objeto a
                // firmar y la almacenamos
                // para mantener un listado con todas. En el caso de las hojas
                // de estilo lo creamos con un
                // identificador descriptivo
                String referenceId = null;
                if ((currentNodeAttributes.getNamedItem("Id") != null && currentNodeAttributes.getNamedItem("Id")  //$NON-NLS-1$//$NON-NLS-2$
                                                                                              .getNodeValue()
                                                                                              .startsWith(STYLE_REFERENCE_PREFIX))) { 
                    referenceId = STYLE_REFERENCE_PREFIX + UUID.randomUUID().toString(); 
                }
                else {
                    referenceId = "Reference-" + UUID.randomUUID().toString(); //$NON-NLS-1$
                }
                referencesIds.add(referenceId);

                // Creamos la propia referencia con las transformaciones de la
                // original
                referenceList.add(fac.newReference(((Element) currentElement).getAttribute("URI"), digestMethod, currentTransformList, //$NON-NLS-1$
                                                   null,
                                                   referenceId));
            }
        }

        final XAdES_EPES xades =
                (XAdES_EPES) XAdES.newInstance(XAdES.EPES,
                                               xadesNamespace,
                                               XADES_SIGNATURE_PREFIX,
                                               XML_SIGNATURE_PREFIX,
                                               digestMethodAlgorithm,
                                               rootSig);

        // establece el certificado
        final X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
        xades.setSigningCertificate(cert);

        // SignaturePolicyIdentifier
        final SignaturePolicyIdentifier spi =
            getPolicy(extraParams.getProperty("policyIdentifier"), //$NON-NLS-1$
                      extraParams.getProperty("policyIdentifierHash"), //$NON-NLS-1$
                      extraParams.getProperty("policyIdentifierHashAlgorithm"), //$NON-NLS-1$
                      extraParams.getProperty("policyDescription"), //$NON-NLS-1$
                      extraParams.getProperty("policyQualifier")); //$NON-NLS-1$
        if (spi != null) {
            xades.setSignaturePolicyIdentifier(spi);
        }

        // SignatureProductionPlace
        final SignatureProductionPlace spp =
                getSignatureProductionPlace(extraParams.getProperty("signatureProductionCity"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionProvince"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionPostalCode"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionCountry")); //$NON-NLS-1$
        if (spp != null) {
            xades.setSignatureProductionPlace(spp);
        }

        // SignerRole
        SignerRole signerRole = null;
        try {
            final String claimedRole = extraParams.getProperty("signerClaimedRole"); //$NON-NLS-1$
            final String certifiedRole = extraParams.getProperty("signerCertifiedRole"); //$NON-NLS-1$
            signerRole = new SignerRoleImpl();
            if (claimedRole != null) {
                signerRole.addClaimedRole(claimedRole);
            }
            if (certifiedRole != null) {
                signerRole.addCertifiedRole(certifiedRole);
            }
        }
        catch (final Exception e) {
            // Se ignoran los errores, son parametros opcionales
        }

        if (signerRole != null) {
            xades.setSignerRole(signerRole);
        }

        // SigningTime
        if (Boolean.parseBoolean(extraParams.getProperty("applySystemDate", Boolean.TRUE.toString()))) { //$NON-NLS-1$ 
            xades.setSigningTime(new Date());
        }

        // crea la firma
        final AOXMLAdvancedSignature xmlSignature;
        try {
            xmlSignature = AOXMLAdvancedSignature.newInstance(xades);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido instanciar la firma Avanzada XML JXAdES", e); //$NON-NLS-1$
        }

        try {
            xmlSignature.setDigestMethod(digestMethodAlgorithm);
            xmlSignature.setCanonicalizationMethod(canonicalizationAlgorithm);
        }
        catch (final Exception e) {
            LOGGER.severe("No se ha podido establecer el algoritmo de huella digital (" + algoUri //$NON-NLS-1$
                                                     + "), es posible que el usado en la firma difiera del indicado: " //$NON-NLS-1$
                                                     + e);
        }

        // Cadena de certificados
        final Certificate[] rawcerts = keyEntry.getCertificateChain();
        final List<X509Certificate> certificates = new ArrayList<X509Certificate>(rawcerts.length);
        for (final Certificate c : rawcerts) {
            if (c instanceof X509Certificate) {
                certificates.add((X509Certificate)c);
            }
        }
        
        try {
            xmlSignature.sign(certificates, keyEntry.getPrivateKey(), algoUri, referenceList, "Signature-" + UUID.randomUUID().toString(), null/*TSA*/); //$NON-NLS-1$
        }
        catch (final NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + algorithm + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (final Exception e) {
            throw new AOException("Error al generar la cofirma", e); //$NON-NLS-1$
        }

        return Utils.writeXML(rootSig, originalXMLProperties, null, null);
    }

    /** Cofirma datos en formato XAdES.
     * <p>
     *  Este m&eacute;todo firma todas las referencias a datos declaradas en la firma original,
     *  ya apunten estas a datos, hojas de estilo o cualquier otro elemento. En cada referencia
     *  firmada se introduciran las mismas transformaciones que existiesen en la firma original.
     * </p>
     * <p>
     *  A nivel de formato interno, cuando cofirmamos un documento ya firmado previamente, esta 
     *  firma previa no se modifica. Si tenemos en cuenta que XAdES es en realidad un subconjunto 
     *  de XMLDSig, el resultado de una cofirma XAdES sobre un documento firmado previamente con 
     *  XMLDSig (o viceversa), son dos firmas independientes, una en XAdES y otra en XMLDSig.<br>
     *  Dado que todas las firmas XAdES son XMLDSig pero no todas las firmas XMLDSig son XAdES, 
     *  el resultado global de la firma se adec&uacute;a al est�ndar m�s amplio, XMLDSig en este caso.
     * </p>
     * @param sign Documento con las firmas iniciales. 
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param keyEntry Entrada que apunta a la clave privada a usar para firmar.
     * @param extraParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>xParams</code>:</p>
     * <dl>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>Identificador de la pol&iacute;tica de firma (normalmente una URL hacia la pol&iacute;tica en formato XML procesable)</dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normlamente del mismo fichero en formato XML procesable).
     *    Si no se indica, es obligatorio que el par&aacute;metro <code>policyIdentifier</code> sea una URL accesible universalmente 
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>
     *  <dt><b><i>policyDescription</i></b></dt>
     *   <dd>Descripci&oacute;n textual de la pol&iacute;tica</dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>URL hacia el documento (legible por personas, normalmente en formato PDF) descriptivo de la pol&iacute;tica de firma</dd>
     *  <dt><b><i>signerClaimedRole</i></b></dt>
     *   <dd>Cargo atribuido para el firmante</dd>
     *  <dt><b><i>signerCertifiedRole</i></b></dt>
     *   <dd>Cargo confirmado para el firmante</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionProvince</i></b></dt>
     *   <dd>Provincia en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionPostalCode</i></b></dt>
     *   <dd>C&oacute;digo postal en el que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionCountry</i></b></dt>
     *   <dd>Pa&iacute;s en el que se realiza la firma</dd>
     *  <dt><b><i>referencesDigestMethod</i></b></dt>
     *   <dd>
     *    Algoritmo de huella digital a usar en las referencias XML (referencesDigestMethod). Debe indicarse como una URL, 
     *    acept&aacute;ndose los siguientes valores:
     *    <ul>
     *     <li><i>http://www.w3.org/2000/09/xmldsig#sha1</i> (SHA-1)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha256</i> (SHA-256, valor recomendado)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#sha512</i> (SHA-512)</li>
     *     <li><i>http://www.w3.org/2001/04/xmlenc#ripemd160 (RIPEMD-160)</i></li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>canonicalizationAlgorithm</i></b></dt>
     *   <dd>Algoritmo de canonicalizaci&oacute;n</dd>
     *  <dt><b><i>xadesNamespace</i></b></dt>
     *   <dd>URL de definici&oacute;n del espacio de nombres de XAdES (y por extensi&oacute;n, versi&oacute;n de XAdES)</dd>
     *   <dt><b><i>applySystemDate</i></b></dt>
     *   <dd>
     *    Indica si se debe introducir en la firma el atributo <i>signingTime</i> con la fecha actual
     *    del sistema. Por defecto, se encuentra a {@code true}. 
     *   </dd>
     * </dl>
     * @return Cofirma en formato XAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso */
    public byte[] cosign(final byte[] sign, 
                         final String algorithm, 
                         final PrivateKeyEntry keyEntry, 
                         final Properties extraParams) throws AOException {

        // nueva instancia de DocumentBuilderFactory que permita espacio de
        // nombres (necesario para XML)
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // carga la raiz del documento XML de firmas
        // y crea un nuevo documento que contendra solo los datos sin firmar
        final Element rootSig;
        final Element rootData;
        try {
            rootSig = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign)).getDocumentElement();

            final Document docData = dbf.newDocumentBuilder().newDocument();
            rootData = (Element) docData.adoptNode(rootSig.cloneNode(true));

            // Obtiene las firmas y las elimina. Para evitar eliminar firmas de
            // las que cuelgan otras
            // y despues intentar eliminar estas, las buscamos y eliminamos de
            // una en una
            NodeList signatures = rootData.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
            while (signatures.getLength() > 0) {
                rootData.removeChild(signatures.item(0));
                signatures = rootData.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
            }

            docData.appendChild(rootData);
        }
        catch (final Exception ioex) {
            throw new AOException("Error al leer el documento de firmas", ioex); //$NON-NLS-1$
        }

        // convierte el documento de firmas en un InputStream
        final ByteArrayOutputStream baosSig = new ByteArrayOutputStream();
        XMLUtils.writeXML(baosSig, rootSig, false);

        // convierte el documento a firmar en un InputStream
        final ByteArrayOutputStream baosData = new ByteArrayOutputStream();
        XMLUtils.writeXML(baosData, rootData, false);

        return cosign(baosData.toByteArray(), baosSig.toByteArray(), algorithm, keyEntry, extraParams);
    }

    /** Contrafirma firmas en formato XAdES.
     * <p>
     * Este m&eacute;todo contrafirma los nodos de firma indicados de un documento de firma.
     * </p>
     * @param sign Documento con las firmas iniciales. 
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param targetType Mecanismo de selecci&oacute;n de los nodos de firma que se deben
     * contrafirmar.
     * <p>Las distintas opciones son:</p>
     * <ul>
     * <li>Todos los nodos del &aacute;rbol de firma</li>
     * <li>Los nodos hoja del &aacute;rbol de firma</li>
     * <li>Los nodos de firma cuyas posiciones se especifican en <code>target</code></li>
     * <li>Los nodos de firma realizados por los firmantes cuyo <i>Common Name</i> se indica en <code>target</code></li>
     * </ul>
     * <p>Cada uno de estos tipos se define en {@link es.gob.afirma.core.signers.CounterSignTarget}.
     * @param targets Listado de nodos o firmantes que se deben contrafirmar seg&uacute;n el
     * {@code targetType} seleccionado.
     * @param keyEntry Entrada que apunta a la clave privada a usar para firmar.
     * @param xParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>xParams</code>:</p>
     * <dl>
     *  <dt><b><i>encoding</i></b></dt>
     *   <dd>Fuerza la codificaci&oacute;n del XML de salida (utf-8, iso-8859-1,...)</dd>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>Identificador de la pol&iacute;tica de firma (normalmente una URL hacia la pol&iacute;tica en formato XML procesable)</dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normlamente del mismo fichero en formato XML procesable).
     *    Si no se indica, es obligatorio que el par&aacute;metro <code>policyIdentifier</code> sea una URL accesible universalmente 
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>
     *  <dt><b><i>policyDescription</i></b></dt>
     *   <dd>Descripci&oacute;n textual de la pol&iacute;tica</dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>URL hacia el documento (legible por personas, normalmente en formato PDF) descriptivo de la pol&iacute;tica de firma</dd>
     *  <dt><b><i>signerClaimedRole</i></b></dt>
     *   <dd>Cargo atribuido para el firmante</dd>
     *  <dt><b><i>signerCertifiedRole</i></b></dt>
     *   <dd>Cargo confirmado para el firmante</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionProvince</i></b></dt>
     *   <dd>Provincia en la que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionPostalCode</i></b></dt>
     *   <dd>C&oacute;digo postal en el que se realiza la firma</dd>
     *  <dt><b><i>signatureProductionCountry</i></b></dt>
     *   <dd>Pa&iacute;s en el que se realiza la firma</dd>
     *  <dt><b><i>applySystemDate</i></b></dt>
     *   <dd>
     *    Indica si se debe introducir en la firma el atributo <i>signingTime</i> con la fecha actual
     *    del sistema. Por defecto, se encuentra a {@code true}. 
     *   </dd>
     * </dl>
     * @return Contrafirma en formato XAdES.
     * @throws AOException Cuando ocurre cualquier problema durante el proceso */
    public byte[] countersign(final byte[] sign,
                              final String algorithm,
                              final CounterSignTarget targetType,
                              final Object[] targets,
                              final PrivateKeyEntry keyEntry,
                              final Properties xParams) throws AOException {

        final Properties extraParams = (xParams != null) ? xParams : new Properties();

        String encoding = extraParams.getProperty("encoding"); //$NON-NLS-1$
        if ("base64".equalsIgnoreCase(encoding)) { //$NON-NLS-1$
            encoding = XMLConstants.BASE64_ENCODING;
        }
        
        if (sign == null) {
            throw new IllegalArgumentException("El objeto de firma no puede ser nulo"); //$NON-NLS-1$
        }

        final String algoUri = XMLConstants.SIGN_ALGOS_URI.get(algorithm);
        if (algoUri == null) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + algorithm + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        this.algo = algorithm;

        // nueva instancia de DocumentBuilderFactory que permita espacio de
        // nombres (necesario para XML)
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // flag que indica si el documento tiene una firma simple o esta
        // cofirmado
        // por defecto se considera que es un documento cofirmado
        boolean esFirmaSimple = false;

        // se carga el documento XML y su raiz
        final Map<String, String> originalXMLProperties = new Hashtable<String, String>();
        Element root;
        try {
            this.doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign));

            if (encoding == null) {
                encoding = this.doc.getXmlEncoding();
            }

            // Ademas del encoding, sacamos otros datos del doc XML original.
            // Hacemos la comprobacion del base64 por si se establecido desde
            // fuera
            if (encoding != null && !XMLConstants.BASE64_ENCODING.equals(encoding)) {
                originalXMLProperties.put(OutputKeys.ENCODING, encoding);
            }
            String tmpXmlProp = this.doc.getXmlVersion();
            if (tmpXmlProp != null) {
                originalXMLProperties.put(OutputKeys.VERSION, tmpXmlProp);
            }
            final DocumentType dt = this.doc.getDoctype();
            if (dt != null) {
                tmpXmlProp = dt.getSystemId();
                if (tmpXmlProp != null) {
                    originalXMLProperties.put(OutputKeys.DOCTYPE_SYSTEM, tmpXmlProp);
                }
            }

            root = this.doc.getDocumentElement();

            // si no es un documento cofirma se anade temporalmente el nodo raiz
            // AFIRMA
            // para que las operaciones de contrafirma funcionen correctamente
            if (root.getNodeName().equals(SIGNATURE_NODE_NAME)) {
                esFirmaSimple = true;
                this.doc = insertarNodoAfirma(this.doc);
                root = this.doc.getDocumentElement();
            }
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma", e); //$NON-NLS-1$
        }

        try {
            if (targetType == CounterSignTarget.TREE) {
                this.countersignTree(root, keyEntry, extraParams, algorithm);
            }
            else if (targetType == CounterSignTarget.LEAFS) {
                this.countersignLeafs(root, keyEntry, extraParams, algorithm);
            }
            else if (targetType == CounterSignTarget.NODES) {
                this.countersignNodes(root, targets, keyEntry, extraParams, algorithm);
            }
            else if (targetType == CounterSignTarget.SIGNERS) {
                this.countersignSigners(root, targets, keyEntry, extraParams, algorithm);
            }
        }
        catch (final Exception e) {
            throw new AOException("Error al generar la contrafirma", e); //$NON-NLS-1$
        }

        // si el documento recibido no estaba cofirmado se elimina el nodo raiz
        // temporal AFIRMA
        // y se vuelve a dejar como raiz el nodo Signature original
        if (esFirmaSimple) {
            try {
                final Document newdoc = dbf.newDocumentBuilder().newDocument();
                newdoc.appendChild(newdoc.adoptNode(this.doc.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG).item(0)));
                this.doc = newdoc;
            }
            catch (final Exception e) {
                LOGGER.info("No se ha eliminado el nodo padre '<AFIRMA>': " + e); //$NON-NLS-1$
            }
        }

        return Utils.writeXML(this.doc.getDocumentElement(), originalXMLProperties, null, null);
    }

    /** Realiza la contrafirma de todos los nodos del arbol
     * @param root
     *        Elemento ra&iacute;z del documento xml que contiene las firmas
     * @param algorithm
     *        Algoritmo de firma XML
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    private void countersignTree(final Element root, final PrivateKeyEntry keyEntry, final Properties extraParams, final String algorithm) throws AOException {

        // obtiene todas las firmas
        final NodeList signatures = root.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
        final int numSignatures = signatures.getLength();

        final Element[] nodes = new Element[numSignatures];
        for (int i = 0; i < numSignatures; i++) {
            nodes[i] = (Element) signatures.item(i);
        }

        // y crea sus contrafirmas
        try {
            for (int i = 0; i < numSignatures; i++) {
                this.cs(nodes[i], keyEntry, extraParams, algorithm);
            }
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma del arbol", e); //$NON-NLS-1$
        }
    }

    /** Realiza la contrafirma de todos los nodos hoja del arbol
     * @param root
     *        Elemento ra&iacute;z del documento xml que contiene las firmas
     * @param algorithm
     *        Algoritmo de firma XML
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    private void countersignLeafs(final Element root, final PrivateKeyEntry keyEntry, final Properties extraParams, final String algorithm) throws AOException {

        // obtiene todas las firmas
        final NodeList signatures = root.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
        int numSignatures = signatures.getLength();

        // comprueba cuales son hojas
        try {
            for (int i = 0; i < numSignatures; i++) {
                final Element signature = (Element) signatures.item(i);
                final int children = signature.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG).getLength();

                // y crea sus contrafirmas
                if (children == 0) {
                    this.cs(signature, keyEntry, extraParams, algorithm);
                    numSignatures++;
                    i++;
                }
            }
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma de hojas", e); //$NON-NLS-1$
        }
    }

    /** Realiza la contrafirma de los nodos indicados en el par&aacute;metro
     * targets
     * @param root
     *        Elemento raiz del documento xml que contiene las firmas
     * @param tgts
     *        Array con las posiciones de los nodos a contrafirmar
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    private void countersignNodes(final Element root,
                                  Object[] tgts,
                                  final PrivateKeyEntry keyEntry,
                                  final Properties extraParams,
                                  final String algorithm) throws AOException {

        // descarta las posiciones que esten repetidas
        final List<Integer> targetsList = new ArrayList<Integer>();
        for (int i = 0; i < tgts.length; i++) {
            if (!targetsList.contains(tgts[i])) {
                targetsList.add((Integer) tgts[i]);
            }
        }
        Object[] targets = targetsList.toArray();

        // obtiene todas las firmas
        final NodeList signatures = root.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);

        // obtiene los nodos indicados en targets
        final Element[] nodes = new Element[targets.length];
        try {
            for (int i = 0; i < targets.length; i++) {
                nodes[i] = (Element) signatures.item(((Integer) targets[i]).intValue());
                if (nodes[i] == null) {
                    throw new AOException("Posicion de nodo no valida."); //$NON-NLS-1$
                }
            }
        }
        catch (final ClassCastException e) {
            throw new AOException("Valor de nodo no valido", e); //$NON-NLS-1$
        }

        // y crea sus contrafirmas
        try {
            for (final Element node : nodes) {
                this.cs(node, keyEntry, extraParams, algorithm);
            }
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma de nodos", e); //$NON-NLS-1$
        }
    }

    /** Realiza la contrafirma de los firmantes indicados en el par&aacute;metro
     * targets
     * @param root
     *        Elemento ra&iacute;z del documento xml que contiene las firmas
     * @param targets
     *        Array con el nombre de los firmantes de los nodos a
     *        contrafirmar
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    private void countersignSigners(final Element root,
                                    final Object[] targets,
                                    final PrivateKeyEntry keyEntry,
                                    final Properties extraParams,
                                    final String algorithm) throws AOException {

        // obtiene todas las firmas
        final NodeList signatures = root.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);

        final List<Object> signers = Arrays.asList(targets);
        final List<Element> nodes = new ArrayList<Element>();

        // obtiene los nodos de los firmantes indicados en targets
        for (int i = 0; i < signatures.getLength(); i++) {
            final Element node = (Element) signatures.item(i);
            if (signers.contains(AOUtil.getCN(Utils.getCertificate(node.getElementsByTagNameNS(XMLConstants.DSIGNNS, "X509Certificate").item(0))))) { //$NON-NLS-1$
                nodes.add(node);
            }
        }

        // y crea sus contrafirmas
        final Iterator<Element> i = nodes.iterator();
        while (i.hasNext()) {
            this.cs(i.next(), keyEntry, extraParams, algorithm);
        }
    }

    /** Realiza la contrafirma de la firma pasada por par&aacute;metro
     * @param signature
     *        Elemento con el nodo de la firma a contrafirmar
     * @param algorithm
     *        Algoritmo de firma XML
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    private void cs(final Element signature, final PrivateKeyEntry keyEntry, final Properties xParams, final String algorithm) throws AOException {

        final Properties extraParams = (xParams != null) ? xParams : new Properties();

        final String digestMethodAlgorithm = extraParams.getProperty("referencesDigestMethod", DIGEST_METHOD); //$NON-NLS-1$
        final String canonicalizationAlgorithm = extraParams.getProperty("canonicalizationAlgorithm", CanonicalizationMethod.INCLUSIVE); //$NON-NLS-1$
        final String xadesNamespace = extraParams.getProperty("xadesNamespace", XADESNS); //$NON-NLS-1$

        // crea un nodo CounterSignature
        final Element counterSignature = this.doc.createElement(XADES_SIGNATURE_PREFIX + ":CounterSignature"); //$NON-NLS-1$

        // recupera o crea un nodo UnsignedSignatureProperties
        final NodeList usp = signature.getElementsByTagNameNS(xadesNamespace, "UnsignedSignatureProperties"); //$NON-NLS-1$
        Element unsignedSignatureProperties;
        if (usp.getLength() == 0) {
            unsignedSignatureProperties = this.doc.createElement(XADES_SIGNATURE_PREFIX + ":UnsignedSignatureProperties"); //$NON-NLS-1$
        }
        else {
            unsignedSignatureProperties = (Element) usp.item(0);
        }

        unsignedSignatureProperties.appendChild(counterSignature);

        // recupera o crea un nodo UnsignedProperties
        final NodeList up = signature.getElementsByTagNameNS(xadesNamespace, "UnsignedProperties"); //$NON-NLS-1$
        final Element unsignedProperties;
        if (up.getLength() == 0) {
            unsignedProperties = this.doc.createElement(XADES_SIGNATURE_PREFIX + ":UnsignedProperties"); //$NON-NLS-1$
        }
        else {
            unsignedProperties = (Element) up.item(0);
        }

        unsignedProperties.appendChild(unsignedSignatureProperties);

        // inserta el nuevo nodo en QualifyingProperties
        final Node qualifyingProperties = signature.getElementsByTagNameNS(xadesNamespace, "QualifyingProperties").item(0); //$NON-NLS-1$
        qualifyingProperties.appendChild(unsignedProperties);

        // obtiene el nodo SignatureValue
        final Element signatureValue = (Element) signature.getElementsByTagNameNS(XMLConstants.DSIGNNS, "SignatureValue").item(0); //$NON-NLS-1$

        // crea la referencia a la firma que se contrafirma
        final List<Reference> referenceList = new ArrayList<Reference>();
        final XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM"); //$NON-NLS-1$
        final DigestMethod digestMethod;
        try {
            digestMethod = fac.newDigestMethod(digestMethodAlgorithm, null);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido obtener un generador de huellas digitales para el algoritmo '" + digestMethodAlgorithm + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        final String referenceId = "Reference-" + UUID.randomUUID().toString(); //$NON-NLS-1$

        try {
            // Transformada para la canonicalizacion inclusiva con comentarios
            final List<Transform> transformList = new ArrayList<Transform>();
            transformList.add(fac.newTransform(canonicalizationAlgorithm, (TransformParameterSpec) null));

            // Aunque el metodo utilizado para generar las contrafirmas hacen
            // que no sea necesario
            // indicar el tipo de la referencia, lo agregamos por si resultase
            // de utilidad
            referenceList.add(fac.newReference("#" + signatureValue.getAttribute("Id"), digestMethod, transformList, CSURI, referenceId)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma", e); //$NON-NLS-1$
        }

        // nueva instancia XADES_EPES del nodo a contrafirmar
        final XAdES_EPES xades =
                (XAdES_EPES) XAdES.newInstance(XAdES.EPES,
                                               xadesNamespace,
                                               XADES_SIGNATURE_PREFIX,
                                               XML_SIGNATURE_PREFIX,
                                               digestMethodAlgorithm,
                                               counterSignature);

        // establece el certificado
        final X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
        xades.setSigningCertificate(cert);

        // SignaturePolicyIdentifier
        final SignaturePolicyIdentifier spi =
            getPolicy(extraParams.getProperty("policyIdentifier"), //$NON-NLS-1$
                      extraParams.getProperty("policyIdentifierHash"), //$NON-NLS-1$
                      extraParams.getProperty("policyIdentifierHashAlgorithm"), //$NON-NLS-1$
                      extraParams.getProperty("policyDescription"), //$NON-NLS-1$
                      extraParams.getProperty("policyQualifier")); //$NON-NLS-1$
        if (spi != null) {
            xades.setSignaturePolicyIdentifier(spi);
        }

        // SignatureProductionPlace
        final SignatureProductionPlace spp =
                getSignatureProductionPlace(extraParams.getProperty("signatureProductionCity"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionProvince"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionPostalCode"), //$NON-NLS-1$
                                            extraParams.getProperty("signatureProductionCountry")); //$NON-NLS-1$
        if (spp != null) {
            xades.setSignatureProductionPlace(spp);
        }

        // SignerRole
        SignerRole signerRole = null;
        try {
            final String claimedRole = extraParams.getProperty("signerClaimedRole"); //$NON-NLS-1$
            final String certifiedRole = extraParams.getProperty("signerCertifiedRole"); //$NON-NLS-1$
            signerRole = new SignerRoleImpl();
            if (claimedRole != null) {
                signerRole.addClaimedRole(claimedRole);
            }
            if (certifiedRole != null) {
                signerRole.addCertifiedRole(certifiedRole);
            }
        }
        catch (final Exception e) {
            // Se ignoran los errores, los parametros son opcionales
        }
        if (signerRole != null) {
            xades.setSignerRole(signerRole);
        }

        // SigningTime
        if (Boolean.parseBoolean(extraParams.getProperty("applySystemDate", Boolean.TRUE.toString()))) { //$NON-NLS-1$ 
            xades.setSigningTime(new Date());
        }

        // crea la firma
        final AOXMLAdvancedSignature xmlSignature;
        try {
            xmlSignature = AOXMLAdvancedSignature.newInstance(xades);
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido instanciar la firma Avanzada XML JXAdES", e); //$NON-NLS-1$
        }
        try {
            xmlSignature.setDigestMethod(digestMethodAlgorithm);
            xmlSignature.setCanonicalizationMethod(canonicalizationAlgorithm);
        }
        catch (final Exception e) {
            LOGGER.severe("No se ha podido establecer el algoritmo de huella digital (" + XMLConstants.SIGN_ALGOS_URI.get(this.algo) //$NON-NLS-1$
                                                     + "), es posible que el usado en la firma difiera del indicado: " //$NON-NLS-1$
                                                     + e);
        }
        
        // Cadena de certificados
        final Certificate[] rawcerts = keyEntry.getCertificateChain();
        final List<X509Certificate> certificates = new ArrayList<X509Certificate>(rawcerts.length);
        for (final Certificate c : rawcerts) {
            if (c instanceof X509Certificate) {
                certificates.add((X509Certificate)c);
            }
        }
        
        try {
            xmlSignature.sign(certificates, keyEntry.getPrivateKey(), XMLConstants.SIGN_ALGOS_URI.get(algorithm), referenceList, "Signature-" + UUID.randomUUID() //$NON-NLS-1$
                                                                                                                               .toString(), null /* TSA */
            );
        }
        catch (final NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("Los formatos de firma XML no soportan el algoritmo de firma '" + this.algo + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (final Exception e) {
            throw new AOException("No se ha podido realizar la contrafirma", e); //$NON-NLS-1$
        }
    }

    /** {@inheritDoc} */
    public AOTreeModel getSignersStructure(final byte[] sign, final boolean asSimpleSignInfo) {

        // Obtenemos el arbol del documento
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        final Document signDoc;
        try {
            signDoc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign));
        }
        catch (final Exception e) {
            LOGGER.warning("Se ha producido un error al obtener la estructura de firmas: " + e); //$NON-NLS-1$
            return null;
        }

        // Obtenemos todas las firmas del documento y el SignatureValue de cada
        // una de ellas
        final NodeList signatures = signDoc.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);

        // Mantendremos 3 listas: la de identificadores de firma, la de
        // identificadores a las
        // que referencia cada firma (cadena vacia salvo para las contrafirmas)
        // y los objetos
        // con los que representaremos cada uno de los nodos de firma.
        final List<String> arrayIds = new ArrayList<String>();
        final List<String> arrayRef = new ArrayList<String>();
        final List<AOTreeNode> arrayNodes = new ArrayList<AOTreeNode>();

        // Rellenamos cada las listas con los datos de las firmas del documento
        for (int i = 0; i < signatures.getLength(); i++) {
            final Element signature = (Element) signatures.item(i);

            // Recogemos el identificador del nodo de firma
            arrayIds.add(signature.getAttribute("Id")); //$NON-NLS-1$

            // Recogemos los objetos que identificaran a los nodos de firma
            arrayNodes.add(new AOTreeNode(asSimpleSignInfo ? Utils.getSimpleSignInfoNode(Utils.guessXAdESNamespaceURL(signDoc.getDocumentElement()),
                                                                                       signature) : Utils.getStringInfoNode(signature)));

            // Recogemos el identificador de la firma a la que se referencia (si
            // no es contrafirma sera cadena vacia)
            if (signature.getParentNode().getNodeName().equals(XADES_SIGNATURE_PREFIX + ":CounterSignature")) { //$NON-NLS-1$
                arrayRef.add(Utils.getCounterSignerReferenceId(signature, signDoc.getElementsByTagNameNS(XMLConstants.DSIGNNS, "SignatureValue"))); //$NON-NLS-1$
            }
            else {
                arrayRef.add(""); //$NON-NLS-1$
            }
        }

        // Se crea el que sera el nodo raiz del arbol
        final AOTreeNode treeRoot = new AOTreeNode("Datos"); //$NON-NLS-1$

        // Se crea el arbol componiendo las subrama de cada firma directa de los
        // datos
        for (int i = 0; i < arrayRef.size(); i++) {
            if (arrayRef.get(i).equals("")) { //$NON-NLS-1$
                treeRoot.add(generateSignsTree(i, signatures.getLength() - 1, arrayNodes, arrayIds, arrayRef)[i]);
            }
        }
        return new AOTreeModel(treeRoot, signatures.getLength());
    }

    /** M&eacute;todo recursivo para la obtenci&oacute;n de la estructura de
     * &aacute;rbol
     * @param i
     *        Inicio de lectura del array de identificadores
     * @param j
     *        Inicio de lectura inversa del array de referencias
     * @param arrayNodes
     *        Array de objetos TreeNode
     * @param arrayIds
     *        Array de identificadores
     * @param arrayRef
     *        Array de referencias
     * @return Array de objetos TreeNode */
    private AOTreeNode[] generateSignsTree(final int i,
                                         final int j,
                                         final List<AOTreeNode> arrayNodes,
                                         final List<String> arrayIds,
                                         final List<String> arrayRef) {

        final int max = arrayIds.size();

        if (i < max && j > 0) {
            if (arrayIds.get(i).equals(arrayRef.get(j))) {
                generateSignsTree(i + 1, j - 1, arrayNodes, arrayIds, arrayRef);
            }

            if (i < j) {
                generateSignsTree(i, j - 1, arrayNodes, arrayIds, arrayRef);
            }

            if (!arrayIds.get(i).equals(arrayRef.get(j))) {
                return arrayNodes.toArray(new AOTreeNode[0]);
            }

            generateSignsTree(j, max - 1, arrayNodes, arrayIds, arrayRef);

            arrayNodes.get(i).add(arrayNodes.get(j));
        }

        return arrayNodes.toArray(new AOTreeNode[0]);
    }

    /** {@inheritDoc} */
    public boolean isSign(final byte[] sign) {

        if (sign == null) {
            LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
            return false;
        }

        try {
            // Carga el documento a validar
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            // JXades no captura un nodo de firma si se pasa este como raiz del
            // arbol de firmas, asi
            // que nos vemos obligados a crear un nodo padre, del que colgara
            // todo el arbol de firmas,
            // para que lo detecte correctamente
            final Element rootNode = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign)).getDocumentElement();

            final List<Node> signNodes = new ArrayList<Node>();
            if (rootNode.getNodeName().equals(SIGNATURE_NODE_NAME)) {
                signNodes.add(rootNode);
            }

            final NodeList signatures = rootNode.getElementsByTagNameNS(XMLConstants.DSIGNNS, SIGNATURE_TAG);
            for (int i = 0; i < signatures.getLength(); i++) {
                signNodes.add(signatures.item(i));
            }

            // Si no se encuentran firmas, no es un documento de firma
            if (signNodes.size() == 0 || !checkSignNodes(signNodes)) {
                return false;
            }
        }
        catch (final Exception e) {
            return false;
        }
        return true;
    }

    /** Comprueba que los nodos de firma proporcionados sean firmas en el formato
     * dado.
     * @param signNodes
     *        Listado de nodos de firma.
     * @return Devuelve {@code true} cuando todos los nodos sean firmas en este
     *         formato. */
    private boolean checkSignNodes(final List<Node> signNodes) {

        String xadesNamespace;
        for (final Node signNode : signNodes) {
            xadesNamespace = Utils.guessXAdESNamespaceURL(signNode);
            if (((Element) signNode).getElementsByTagNameNS(xadesNamespace, "QualifyingProperties").getLength() == 0) { //$NON-NLS-1$
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    public boolean isValidDataFile(final byte[] data) {
        if (data == null) {
            LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    public String getSignedName(final String originalName, final String inText) {
        return originalName + (inText != null ? inText : "") + ".xsig"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Devuelve un nuevo documento con ra&iacute;z "AFIRMA" del que cuelga el
     * documento especificado.
     * @param docu
     *        Documento que estar&aacute; contenido en el nuevo documento.
     * @return Documento con ra&iacute;z "AFIRMA".
     * @throws ParserConfigurationException */
    private Document insertarNodoAfirma(final Document docu) throws ParserConfigurationException {

        // Nueva instancia de DocumentBuilderFactory que permita espacio de
        // nombres (necesario para XML)
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // Crea un nuevo documento con la raiz "AFIRMA"
        final Document docAfirma = dbf.newDocumentBuilder().newDocument();
        final Element rootAfirma = docAfirma.createElement(AFIRMA);

        // Inserta el documento pasado por parametro en el nuevo documento
        rootAfirma.appendChild(docAfirma.adoptNode(docu.getDocumentElement()));
        docAfirma.appendChild(rootAfirma);

        return docAfirma;
    }

    /** {@inheritDoc} */
    public AOSignInfo getSignInfo(final byte[] sign) throws AOException {
        if (sign == null) {
            throw new IllegalArgumentException("No se han introducido datos para analizar"); //$NON-NLS-1$
        }

        if (!isSign(sign)) {
            throw new AOInvalidFormatException("Los datos introducidos no se corresponden con un objeto de firma"); //$NON-NLS-1$
        }

        final AOSignInfo signInfo = new AOSignInfo(AOSignConstants.SIGN_FORMAT_XADES);

        // Analizamos mas en profundidad la firma para obtener el resto de datos

        // Tomamos la raiz del documento
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Element rootSig = null;
        try {
            rootSig = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(sign)).getDocumentElement();
        }
        catch (final Exception e) {
            LOGGER.warning("Error al analizar la firma: " + e); //$NON-NLS-1$
            rootSig = null;
        }

        // Establecemos la variante de firma
        if (rootSig != null) {
            if (isDetached(rootSig)) {
                signInfo.setVariant(AOSignConstants.SIGN_FORMAT_XADES_DETACHED);
            }
            else if (isEnveloped(rootSig)) {
                signInfo.setVariant(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPED);
            }
            else if (isEnveloping(rootSig)) {
                signInfo.setVariant(AOSignConstants.SIGN_FORMAT_XADES_ENVELOPING);
            }
        }

        // Aqui vendria el analisis de la firma buscando alguno de los otros
        // datos de relevancia
        // que se almacenan en el objeto AOSignInfo

        return signInfo;
    }

}
