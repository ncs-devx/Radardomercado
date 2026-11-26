# Regras adicionais serão incluídas quando a versão release for assinada.
# Os modelos são desserializados pelo Gson e precisam manter nomes/campos.
-keep class com.radardemercado.app.data.** { *; }
-keepattributes Signature,*Annotation*
