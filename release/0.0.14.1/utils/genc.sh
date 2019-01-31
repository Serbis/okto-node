#!/bin/bash
 
# This script allows you to create a self-signed certificate in a suitable format for the OKTO node.
# This file must be placed in a folder that will be used to store all files.
# associated with certificate nodes. At the initial launch of the script, two center files will be created.
# CA_ALIAS.jks and CA_ALIAS.crt. The first is the private key storage file,
# Encrypted with a phrase defined in CA_PASS. This file is the top of the system.
# certificates. If it is compromised, all nodes will be automatically the same.
# compromised. Therefore, this file should under no circumstances have any publicity.
# The second file is a public root certificate. It must be added to the system from which
# scheduled access at the site. If it is for example a browser, then it must be added to the registry of trusted
# certification authorities. Five target certificate files will also be created. Direct
# of interest is the file NODE_ALIAS.p12. This file is an encrypted key storage by
# phrase NODE_PASS. This file must be placed in the tls directory in the node directory, in the file
# node.conf in node.shell.keystoreFile specify the name of this file and specify in node.shell.keystorePass
# phrase NODE_PASS. When generating subsequent certificates, set the CA_GEN parameter to false,
# This will prevent re-generation of the certificate authority. If you want to re-create some
# certificate, you must first remove all files associated with it.
 
# -------------------------------------------------------------------------------------------------
 
# Create a certification center. The parameter must be true at the first start. With
# subsequent generations need to be changed to false.
CA_GEN=false
 
# This parameter can be not changed, it defines the certification authority alias and file names
# accordingly
CA_ALIAS="nodeca"
 
# Ключевая фраза центра сертификации. Тут нужно подставить надежный пароль
CA_PASS="Aet1emDVg9MXFdSU7StMrNOdXvdRTY"
 
# Алиас сертификата узла. Все файлы целевого сертификата будут иметь это имя
NODE_ALIAS="2002:d040:79a1:5::3"
 
# CN field certificate node. In practice, it denotes the direct address at which will be
# access the node. There may be an IP address or a domain name for which the shell is available.
# In other words, the query string to the shell in the case of the value below should look like this:
# wss: //192.168.88.100: 5000 / shell
NODE_CA="2002:d040:79a1:5::3"
 
# Key phrase storage key. Here you need to substitute a strong password
NODE_PASS="kRWEvnvVv9cY9e1MwBdYGPaCU3m4FG"

#Mode for SAN - IP or DNS
NODE_NM="IP"
 
# -------------------------------------------------------------------------------------------------
 
if [ "$CA_GEN" = true ] ; then
 
keytool -genkeypair -v \
  -alias $CA_ALIAS \
  -dname "CN=nodeCa, OU=ca, O=okto, L=N/A, ST=N/A, C=RU" \
  -keystore $CA_ALIAS.jks \
  -keypass $CA_PASS \
  -storepass $CA_PASS \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999
 
keytool -export -v \
  -alias $CA_ALIAS \
  -file $CA_ALIAS.crt \
  -keypass $CA_PASS \
  -storepass $CA_PASS \
  -keystore $CA_ALIAS.jks \
  -rfc
 
fi
 
keytool -genkeypair -v \
  -alias $NODE_ALIAS \
  -dname "CN=$NODE_CA, OU=usr, O=okto, L=N/A ST=N/A, C=RU" \
  -keystore $NODE_ALIAS.jks \
  -keypass $NODE_PASS \
  -storepass $NODE_PASS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385
 
keytool -certreq -v \
  -alias $NODE_ALIAS \
  -keypass $NODE_PASS \
  -storepass $NODE_PASS \
  -keystore $NODE_ALIAS.jks \
  -file $NODE_ALIAS.csr
 
keytool -gencert -v \
  -alias $CA_ALIAS \
  -keypass $CA_PASS  \
  -storepass $CA_PASS \
  -keystore $CA_ALIAS.jks \
  -infile $NODE_ALIAS.csr \
  -outfile $NODE_ALIAS.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="$NODE_NM:$NODE_CA" \
  -rfc
 
keytool -import -v \
  -alias $CA_ALIAS \
  -file $CA_ALIAS.crt \
  -keystore $NODE_ALIAS.jks \
  -storetype JKS \
  -storepass $NODE_PASS << EOF
yes
EOF
 
keytool -import -v \
  -alias $NODE_ALIAS \
  -file $NODE_ALIAS.crt \
  -keystore $NODE_ALIAS.jks \
  -storetype JKS \
  -storepass $NODE_PASS
 
keytool -importkeystore -srckeystore $NODE_ALIAS.jks -destkeystore $NODE_ALIAS.p12 \
-deststoretype PKCS12 -srcalias $NODE_ALIAS -deststorepass $NODE_PASS \
-srcstorepass $NODE_PASS \
-destkeypass $NODE_PASS

echo $NODE_PASS > $NODE_ALIAS.pass
 
#openssl pkcs12 -in $NODE_ALIAS.p12 -passin pass:$NODE_PASS -nokeys -out $NODE_ALIAS-cert.pem
 
#openssl pkcs12 -in $NODE_ALIAS.p12 -passin pass:$NODE_PASS -nodes -nocerts -out $NODE_ALIAS-key.pem
