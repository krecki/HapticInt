
#include <BluetoothSerial.h>

// UART2 connections to the main grblHAL ESP32
#define UART2_RX_PIN 16
#define UART2_TX_PIN 17

// Bluetooth Classic SPP
BluetoothSerial SerialBT;

const unsigned long STATUS_INTERVAL = 100;

bool pollingEnabled = false;
unsigned long lastStatusRequest = 0;

String usbCommand = "";
String rxLine = "";

bool receivingStatus = false;


void setup()
{
    Serial.begin(115200);
    delay(1000);

    // Start UART2 connection to main controller
    Serial2.begin(115200, SERIAL_8N1, UART2_RX_PIN, UART2_TX_PIN);

    // Start Bluetooth Classic SPP
    if (!SerialBT.begin("CNC_Mill_Pendant"))
    {
        Serial.println("[ERROR] Bluetooth initialization failed.");
    }
    else
    {
        Serial.println("[BLUETOOTH] Started as CNC_Mill_Pendant");
    }

    Serial.println();
    Serial.println("ESP32 Pendant Communication");
    Serial.println("---------------------------------");
    Serial.println("Bluetooth: CNC_Mill_Pendant");
    Serial.println("POLL ON  - Start USB position polling");
    Serial.println("POLL OFF - Stop USB position polling");
    Serial.println("Other USB commands are forwarded to grblHAL.");
    Serial.println("---------------------------------");
}


void loop()
{
    readUSB();

    readBluetooth();

    readMainController();

    updatePositionPolling();
}


// --------------------------------------------------
// USB SERIAL INPUT
// --------------------------------------------------

void readUSB()
{
    while (Serial.available())
    {
        char c = Serial.read();

        if (c == '\r') continue;

        if (c == '\n')
        {
            usbCommand.trim();

            if (usbCommand.length() > 0)
            {
                processUSBCommand(usbCommand);
            }

            usbCommand = "";
        }
        else
        {
            usbCommand += c;
        }
    }
}


void processUSBCommand(String command)
{
    if (command.equalsIgnoreCase("POLL ON"))
    {
        pollingEnabled = true;

        requestPosition();

        Serial.println("[POLL] Position polling enabled.");
    }
    else if (command.equalsIgnoreCase("POLL OFF"))
    {
        pollingEnabled = false;

        Serial.println("[POLL] Position polling disabled.");
    }
    else
    {
        Serial2.print(command);
        Serial2.write('\n');

        Serial.print("[TX USB] ");
        Serial.println(command);
    }
}


// --------------------------------------------------
// BLUETOOTH INPUT
// --------------------------------------------------

void readBluetooth()
{
    while (SerialBT.available())
    {
        int incoming = SerialBT.read();

        if (incoming >= 0)
        {
            // Forward Bluetooth data directly to grblHAL.
            // This includes realtime characters such as '?'.
            Serial2.write((uint8_t)incoming);
        }
    }
}


// --------------------------------------------------
// POSITION POLLING
// --------------------------------------------------

void requestPosition()
{
    Serial2.write('?');

    lastStatusRequest = millis();
}


void updatePositionPolling()
{
    if (!pollingEnabled) return;

    if (millis() - lastStatusRequest >= STATUS_INTERVAL)
    {
        requestPosition();
    }
}


// --------------------------------------------------
// MAIN CONTROLLER INPUT
// --------------------------------------------------

void readMainController()
{
    while (Serial2.available())
    {
        char c = Serial2.read();

        // Forward every received character to the phone.
        // The phone's Bluetooth manager will assemble messages.
        SerialBT.write((uint8_t)c);

        // Keep the existing USB diagnostic output.
        if (c == '<')
        {
            receivingStatus = true;
            rxLine = "<";
            continue;
        }

        if (receivingStatus)
        {
            rxLine += c;

            if (c == '>')
            {
                Serial.print("[STATUS] ");
                Serial.println(rxLine);

                rxLine = "";
                receivingStatus = false;
            }

            continue;
        }

        if (c == '\n')
        {
            rxLine.trim();

            if (rxLine.length() > 0)
            {
                Serial.print("[RX] ");
                Serial.println(rxLine);
            }

            rxLine = "";
        }
        else if (c != '\r')
        {
            rxLine += c;
        }
    }
}
