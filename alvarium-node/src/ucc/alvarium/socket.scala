package ucc.alvarium

import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.*

private val store = os.pwd / "res" / "servercert.p12"

private def getContext = {
  val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
  val tstore = os.read.inputStream(store)
  trustStore.load(tstore, "abc123".toCharArray)
  tstore.close()
  val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
  tmf.init(trustStore)

  val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
  val kstore = os.read.inputStream(store)
  keyStore.load(kstore, "abc123".toCharArray)
  val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
  kmf.init(keyStore, "abc123".toCharArray)
  val ctx = SSLContext.getInstance("TLS")
  ctx.init(kmf.getKeyManagers, tmf.getTrustManagers, SecureRandom.getInstanceStrong)
  ctx
}

def getClientSocket(address: String) = {
  val factory = getContext.getSocketFactory

  val connection = factory.createSocket(address, 5540).asInstanceOf[SSLSocket]
  connection.setEnabledProtocols(Array[String]("TLSv1.2"))
  val sslParams = new SSLParameters()
  sslParams.setEndpointIdentificationAlgorithm("HTTPS")
  connection.setSSLParameters(sslParams)
  connection
}


def getServerSocket = {
  val factory = getContext.getServerSocketFactory
  val listener = factory.createServerSocket(5540)
  val sslListener = listener.asInstanceOf[SSLServerSocket]
  sslListener.setNeedClientAuth(true)
  sslListener.setEnabledProtocols(Array[String]("TLSv1.2"))

  sslListener
}