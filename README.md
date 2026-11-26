# Radar de Mercado

Aplicativo Android privado para monitorar BTC, ETH, SOL e outros ativos em USDT usando endpoints públicos da Binance.

## Versão 1.4.2

- Cotações Binance por REST com WebSocket público enquanto o app está aberto.
- Reconexão automática, indicador de cotação desatualizada e horário da última atualização.
- Cache local de preços e notícias para abertura sem internet.
- Cache de 6 horas da lista de ativos da Binance para reduzir tráfego e acelerar a inicialização.
- Alertas recorrentes por cruzamento acima/abaixo, histórico, simulação, linha de alvo e criação pelo gráfico.
- Som e vibração configuráveis, teste de notificação e registro da última checagem em segundo plano.
- Notícias em carrossel horizontal com link para a fonte original.
- Chave Twelve Data criptografada pelo Android Keystore.
- APK release reduzido e assinado por uma keystore exclusiva.

## Compilar

```powershell
Set-Location "D:\Apps\RadarDeMercado"
& "C:\Windows\Temp\radar-android-tools\build2\gradle-8.9\bin\gradle.bat" --no-daemon testDebugUnitTest assembleDebug assembleRelease
```

APKs finais:

- `outputs/app-debug.apk`
- `outputs/Radar-de-Mercado-1.4.2-release.apk`

Faça backup conjunto de `app/radar-release.keystore` e `keystore.properties`. Sem esses arquivos não será possível publicar uma atualização assinada sobre o mesmo APK release instalado.

## Limitação do Android

O WorkManager faz verificações periódicas em segundo plano, com intervalo mínimo aproximado de 15 minutos, mas o Android pode adiá-las por bateria, suspensão ou restrições do fabricante. Alertas realmente instantâneos durante 24 horas, mesmo com o aplicativo encerrado, exigem servidor e push em uma versão futura.

Enquanto o app está aberto, uma conexão WebSocket pública combinada atualiza os três ativos simultaneamente e avalia alertas em tempo real. REST permanece como carga inicial e fallback; nenhuma credencial ou operação de compra/venda é utilizada.

## Executar

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Inicie um emulador Android 13 ou superior.
4. Execute a configuração `app`.

## Testar alertas

1. Em **Alertas**, crie um alerta para BTC/USDT.
2. Escolha “subir acima” e informe um alvo.
3. Abra **Mais > Simulador de preços**.
4. Selecione BTC/USDT e informe um preço igual ou maior que o alvo.
5. Execute a simulação e confira a notificação e o Histórico.

O WorkManager verifica alertas em segundo plano com intervalo mínimo aproximado de 15 minutos. O Android pode atrasar esse trabalho quando o aplicativo está fechado ou sob restrições de bateria. Monitoramento realmente instantâneo e contínuo exigirá backend e push notification.
