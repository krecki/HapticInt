
#include <BluetoothSerial.h>

// UART2 connections to the main grblHAL ESP32
#define UART2_RX_PIN 16
#define UART2_TX_PIN 17

// Bluetooth Classic SPP
BluetoothSerial SerialBT;


void setup()
{
    // Start UART2 connection to main grblHAL controller
    Serial2.begin(115200, SERIAL_8N1, UART2_RX_PIN, UART2_TX_PIN);

    // Start Bluetooth Classic SPP
    SerialBT.begin("CNC_Mill_Pendant");
}


void loop()
{
    readBluetooth();

    readMainController();
}


// --------------------------------------------------
// BLUETOOTH -> MAIN CONTROLLER
// --------------------------------------------------

void readBluetooth()
{
    while (SerialBT.available())
    {
        int incoming = SerialBT.read();

        if (incoming >= 0)
        {
            // Forward raw bytes without modification.
            Serial2.write((uint8_t)incoming);
        }
    }
}


// --------------------------------------------------
// MAIN CONTROLLER -> BLUETOOTH
// --------------------------------------------------

void readMainController()
{
    while (Serial2.available())
    {
        int incoming = Serial2.read();

        if (incoming >= 0)
        {
            // Forward raw bytes without modification.
            SerialBT.write((uint8_t)incoming);
        }
    }
}
