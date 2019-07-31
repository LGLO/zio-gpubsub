package zio.gpubsub

import scala.io.Source
import org.specs2.Specification

class KeyUtilSpec extends Specification {
  val privateKeyFileLines = Source.fromInputStream(getClass.getResourceAsStream("/rsa_private.pem")).getLines

  def is = "KeyUtil".title ^ s2"""
    parses private RSA key from private key file lines $parsePrivateRsa
    """

  def parsePrivateRsa = {
    val str = privateKeyFileLines.mkString("\n")
    val privateKey = KeyUtil.parsePrivateRsaKey(str)
    privateKey.getAlgorithm() === "RSA" and privateKey.getFormat() === "PKCS#8"
  }
}
