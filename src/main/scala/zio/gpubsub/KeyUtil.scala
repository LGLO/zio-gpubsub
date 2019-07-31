package zio.gpubsub

import java.security.{KeyFactory, PrivateKey}
import java.security.spec.PKCS8EncodedKeySpec

object KeyUtil {

  private val keyDecoder = java.util.Base64.getDecoder()

  def parsePrivateRsaKey(privateKey: String): PrivateKey = {
    val trimmedKey = privateKey
      .replaceAll("-----.*-----", "")
      .replaceAll("\r\n", "")
      .replaceAll("\n", "")
      .trim
    val decoded = keyDecoder.decode(trimmedKey)
    val spec = new PKCS8EncodedKeySpec(decoded)
    KeyFactory.getInstance("RSA", "SunRsaSign").generatePrivate(spec)
  }
}
