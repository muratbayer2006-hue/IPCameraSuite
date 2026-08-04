#!/usr/bin/env python3
import subprocess
import sys
import os
import signal
from datetime import datetime
import asyncio
import json
import websockets
import threading

COLOR_RESET = "\033[0m"
COLOR_RED = "\033[91m"
COLOR_GREEN = "\033[92m"
COLOR_YELLOW = "\033[93m"
COLOR_BLUE = "\033[94m"
COLOR_MAGENTA = "\033[95m"
COLOR_CYAN = "\033[96m"
COLOR_WHITE = "\033[97m"
COLOR_GRAY = "\033[90m"

LOG_FILE = "console_log.txt"
log_file_handle = None

ws_connection = None
use_websocket = False
websocket_url = ""
ws_listener_task = None
ws_event_loop = None

def log_to_file(message):
    global log_file_handle
    try:
        if log_file_handle is None:
            log_file_handle = open(LOG_FILE, "a")
        log_file_handle.write(message + "\n")
        log_file_handle.flush()
    except:
        pass

def print_colored(color, message):
    print(f"{color}{message}{COLOR_RESET}")
    log_to_file(message)

def print_header():
    os.system('clear')
    print_colored(COLOR_CYAN, "╔══════════════════════════════════════════╗")
    print_colored(COLOR_CYAN, "║      IPCameraSuite Developer Console     ║")
    print_colored(COLOR_CYAN, "╚══════════════════════════════════════════╝")
    print()

def check_device():
    print_colored(COLOR_YELLOW, ">>> Telefon bağlantısı kontrol ediliyor...")
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    lines = result.stdout.splitlines()
    device_found = False
    for line in lines:
        if "device" in line and "List" not in line:
            device_found = True
            serial = line.split()[0]
            print_colored(COLOR_GREEN, f"✅ Telefon bağlı: {serial}")
            log_to_file(f"Device connected: {serial}")
            break
    if not device_found:
        print_colored(COLOR_RED, "❌ Telefon bağlı değil! Lütfen USB bağlantısını kontrol et.")
    return device_found

def send_command(command):
    global use_websocket
    if use_websocket and ws_connection is not None:
        asyncio.run_coroutine_threadsafe(send_ws_command(command), ws_event_loop)
    else:
        send_adb_command(command)

def send_adb_command(command):
    cmd = [
        "adb", "shell", "am", "broadcast",
        "-a", "com.ipcamerasuite.COMMAND",
        "--es", "command", command,
        "-n", "com.ipcamerasuite/.CommandReceiver"
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode == 0 and "Broadcast completed: result=0" in result.stdout:
        print_colored(COLOR_GREEN, f"✅ Komut gönderildi (ADB): {command}")
    else:
        print_colored(COLOR_RED, f"❌ Komut gönderilemedi (ADB): {command}")

async def send_ws_command(command):
    global ws_connection
    try:
        if ws_connection is None:
            print_colored(COLOR_RED, "❌ WebSocket bağlantısı yok!")
            return
        message = json.dumps({"command": command})
        await ws_connection.send(message)
        print_colored(COLOR_GREEN, f"✅ Komut gönderildi (WebSocket): {command}")
    except Exception as e:
        print_colored(COLOR_RED, f"❌ WebSocket hatası: {e}")

async def listen_ws_messages():
    global ws_connection
    try:
        while ws_connection is not None:
            try:
                message = await ws_connection.recv()
                data = json.loads(message)
                event = data.get("event", "unknown")
                if event == "stream_started":
                    url = data.get("url", "")
                    print_colored(COLOR_GREEN, f"📡 Yayın başladı: {url}")
                elif event == "stream_stopped":
                    print_colored(COLOR_YELLOW, "⏹ Yayın durduruldu")
                elif event == "camera_switched":
                    isBack = data.get("isBack", True)
                    cam = "Arka" if isBack else "Ön"
                    print_colored(COLOR_MAGENTA, f"🔄 Kamera değiştirildi: {cam} kamera")
                elif event == "analysis_started":
                    print_colored(COLOR_BLUE, "🔬 Analiz başlatıldı")
                elif event == "analysis_stopped":
                    print_colored(COLOR_BLUE, "🔬 Analiz durduruldu")
                elif event == "connected":
                    print_colored(COLOR_GREEN, "🔗 WebSocket bağlantısı kuruldu")
                elif event == "test_result":
                    test = data.get("test", "unknown")
                    result_raw = data.get("result", "{}")
                    try:
                        result_json = json.loads(result_raw)
                        status = result_json.get("status", "UNKNOWN")
                        if status == "PASS":
                            print_colored(COLOR_GREEN, f"✅ {test.upper()} Testi BAŞARILI")
                            for key in result_json.keys():
                                if key not in ["status"]:
                                    print_colored(COLOR_WHITE, f"   {key}: {result_json[key]}")
                        else:
                            error = result_json.get("error", "Bilinmeyen hata")
                            print_colored(COLOR_RED, f"❌ {test.upper()} Testi BAŞARISIZ: {error}")
                    except Exception as e:
                        print_colored(COLOR_RED, f"❌ Test sonucu parse hatası: {e}")
                        print_colored(COLOR_WHITE, f"📩 Ham sonuç: {result_raw}")
                else:
                    print_colored(COLOR_WHITE, f"📩 Bilinmeyen olay: {message}")
            except websockets.exceptions.ConnectionClosed:
                print_colored(COLOR_YELLOW, "🔌 WebSocket bağlantısı kapatıldı")
                break
            except Exception as e:
                print_colored(COLOR_RED, f"❌ Mesaj okuma hatası: {e}")
                break
    except Exception as e:
        print_colored(COLOR_RED, f"❌ Dinleyici hatası: {e}")
    finally:
        ws_connection = None
        global use_websocket
        use_websocket = False

def stream_logs():
    print_colored(COLOR_YELLOW, ">>> Canlı loglar başlatılıyor (Ctrl+C ile durdur)...")
    subprocess.run(["adb", "logcat", "-c"], capture_output=True)
    process = subprocess.Popen(
        ["adb", "logcat", "-s", "IPCameraSuite:D"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )
    try:
        for line in process.stdout:
            timestamp = datetime.now().strftime("%H:%M:%S")
            formatted = f"[{timestamp}] {line.strip()}"
            if "ERR" in line or "ERROR" in line:
                print_colored(COLOR_RED, formatted)
            elif "SUC" in line or "SUCCESS" in line:
                print_colored(COLOR_GREEN, formatted)
            elif "WRN" in line or "WARNING" in line:
                print_colored(COLOR_YELLOW, formatted)
            elif "CAM" in line or "NET" in line or "STR" in line:
                print_colored(COLOR_MAGENTA, formatted)
            else:
                print_colored(COLOR_WHITE, formatted)
    except KeyboardInterrupt:
        pass
    finally:
        process.terminate()

def pull_logs():
    print_colored(COLOR_YELLOW, ">>> Log dosyaları taranıyor...")
    cmd_ls = ["adb", "shell", "run-as", "com.ipcamerasuite", "ls", "/data/data/com.ipcamerasuite/files/logs/"]
    result = subprocess.run(cmd_ls, capture_output=True, text=True)
    if result.returncode != 0 or not result.stdout.strip():
        print_colored(COLOR_RED, "❌ Log dosyası bulunamadı!")
        return
    files = result.stdout.strip().splitlines()
    if not files:
        print_colored(COLOR_RED, "❌ Log klasörü boş!")
        return
    latest = sorted(files)[-1]
    print_colored(COLOR_GREEN, f"📄 En son log dosyası: {latest}")
    target = os.path.expanduser(f"~/Documents/{latest}")
    cmd_pull = ["adb", "exec-out", "run-as", "com.ipcamerasuite", "cat", f"/data/data/com.ipcamerasuite/files/logs/{latest}"]
    with open(target, "wb") as f:
        proc = subprocess.Popen(cmd_pull, stdout=f, stderr=subprocess.PIPE)
        proc.wait()
    if proc.returncode == 0:
        print_colored(COLOR_GREEN, f"✅ Log dosyası kaydedildi: {target}")
    else:
        print_colored(COLOR_RED, "❌ Log dosyası çekilemedi!")

def clear_logs():
    print_colored(COLOR_YELLOW, ">>> Log dosyaları temizleniyor...")
    cmd_rm = ["adb", "shell", "run-as", "com.ipcamerasuite", "rm", "-rf", "/data/data/com.ipcamerasuite/files/logs/"]
    result_rm = subprocess.run(cmd_rm, capture_output=True, text=True)
    if result_rm.returncode != 0:
        print_colored(COLOR_RED, f"❌ Klasör silinemedi: {result_rm.stderr}")
        return
    cmd_mkdir = ["adb", "shell", "run-as", "com.ipcamerasuite", "mkdir", "/data/data/com.ipcamerasuite/files/logs/"]
    result_mkdir = subprocess.run(cmd_mkdir, capture_output=True, text=True)
    if result_mkdir.returncode == 0:
        print_colored(COLOR_GREEN, "✅ Log dosyaları temizlendi ve klasör yeniden oluşturuldu.")
    else:
        print_colored(COLOR_RED, f"❌ Klasör oluşturulamadı: {result_mkdir.stderr}")

def connect_websocket():
    global ws_connection, use_websocket, websocket_url, ws_listener_task, ws_event_loop
    ip = input("Telefon IP adresini girin (örn: 192.168.1.104): ").strip()
    if not ip:
        print_colored(COLOR_RED, "❌ IP adresi boş!")
        return
    port = input("WebSocket portunu girin (varsayılan 8081): ").strip()
    if not port:
        port = "8081"
    try:
        port = int(port)
    except ValueError:
        print_colored(COLOR_RED, "❌ Geçersiz port!")
        return
    websocket_url = f"ws://{ip}:{port}"
    print_colored(COLOR_YELLOW, f">>> WebSocket bağlantısı kuruluyor: {websocket_url}")
    try:
        async def connect():
            global ws_connection
            ws_connection = await websockets.connect(websocket_url)
            print_colored(COLOR_GREEN, f"✅ WebSocket bağlantısı kuruldu: {websocket_url}")
            asyncio.create_task(listen_ws_messages())
            return ws_connection
        ws_event_loop = asyncio.new_event_loop()
        asyncio.set_event_loop(ws_event_loop)
        ws_connection = ws_event_loop.run_until_complete(connect())
        use_websocket = True
        def run_loop():
            ws_event_loop.run_forever()
        threading.Thread(target=run_loop, daemon=True).start()
    except Exception as e:
        print_colored(COLOR_RED, f"❌ WebSocket bağlantısı başarısız: {e}")
        use_websocket = False
        ws_connection = None

async def disconnect_websocket_async():
    global ws_connection
    if ws_connection:
        await ws_connection.close()
        ws_connection = None
        print_colored(COLOR_YELLOW, "🔌 WebSocket bağlantısı kapatıldı")

def disconnect_websocket():
    global use_websocket, ws_event_loop
    try:
        if ws_event_loop is not None and ws_event_loop.is_running():
            asyncio.run_coroutine_threadsafe(disconnect_websocket_async(), ws_event_loop)
            ws_event_loop.call_soon_threadsafe(ws_event_loop.stop)
        use_websocket = False
    except Exception as e:
        print_colored(COLOR_RED, f"❌ Bağlantı kapatılamadı: {e}")

def show_test_menu():
    print_colored(COLOR_CYAN, "═══════════════════════════════════════════")
    print_colored(COLOR_WHITE, "1.  📷 Kamera Testi (test_camera)")
    print_colored(COLOR_WHITE, "2.  📡 Stream Testi (test_stream)")
    print_colored(COLOR_WHITE, "3.  🔙 Ana Menü")
    print_colored(COLOR_CYAN, "═══════════════════════════════════════════")
    return input("Seçiminiz: ").strip()

def show_menu():
    global use_websocket
    print()
    print_colored(COLOR_CYAN, "═══════════════════════════════════════════")
    status = "🔗 WebSocket" if use_websocket else "🔌 ADB"
    print_colored(COLOR_WHITE, f"İletişim Modu: {status}")
    print_colored(COLOR_CYAN, "───────────────────────────────────────────")
    print_colored(COLOR_WHITE, "1.  📋 Bağlantıyı Kontrol Et")
    print_colored(COLOR_WHITE, "2.  📡 Canlı Logları İzle")
    print_colored(COLOR_WHITE, "3.  ▶️  Yayını Başlat (start_stream)")
    print_colored(COLOR_WHITE, "4.  ⏹️  Yayını Durdur (stop_stream)")
    print_colored(COLOR_WHITE, "5.  🔄 Kamera Değiştir (switch_camera)")
    print_colored(COLOR_WHITE, "6.  🔍 Zoom In (zoom_in)")
    print_colored(COLOR_WHITE, "7.  🔍 Zoom Out (zoom_out)")
    print_colored(COLOR_WHITE, "8.  🚪 Çıkış")
    print_colored(COLOR_WHITE, "9.  🔬 Analizi Başlat/Durdur (toggle_analysis)")
    print_colored(COLOR_WHITE, "10. 📥 Log Dosyasını Çek")
    print_colored(COLOR_WHITE, "11. 🗑️ Log Dosyalarını Temizle")
    print_colored(COLOR_WHITE, "12. 🔗 WebSocket'e Bağlan")
    print_colored(COLOR_WHITE, "13. 🔌 WebSocket Bağlantısını Kapat")
    print_colored(COLOR_WHITE, "14. 🧪 Test Çalıştır")
    print_colored(COLOR_CYAN, "═══════════════════════════════════════════")
    return input("Seçiminiz: ").strip()

def main():
    global log_file_handle
    def signal_handler(sig, frame):
        print_colored(COLOR_YELLOW, "\nÇıkış yapılıyor...")
        if log_file_handle:
            log_file_handle.close()
        if use_websocket:
            disconnect_websocket()
        sys.exit(0)
    signal.signal(signal.SIGINT, signal_handler)

    print_header()
    check_device()

    while True:
        choice = show_menu()
        if choice == "1":
            check_device()
        elif choice == "2":
            stream_logs()
        elif choice == "3":
            send_command("start_stream")
        elif choice == "4":
            send_command("stop_stream")
        elif choice == "5":
            send_command("switch_camera")
        elif choice == "6":
            send_command("zoom_in")
        elif choice == "7":
            send_command("zoom_out")
        elif choice == "8":
            print_colored(COLOR_GREEN, "Güle güle!")
            if log_file_handle:
                log_file_handle.close()
            if use_websocket:
                disconnect_websocket()
            sys.exit(0)
        elif choice == "9":
            send_command("toggle_analysis")
        elif choice == "10":
            pull_logs()
        elif choice == "11":
            clear_logs()
        elif choice == "12":
            connect_websocket()
        elif choice == "13":
            if use_websocket:
                disconnect_websocket()
            else:
                print_colored(COLOR_YELLOW, "⚠️ Zaten WebSocket bağlantısı yok.")
        elif choice == "14":
            while True:
                test_choice = show_test_menu()
                if test_choice == "1":
                    send_command("test_camera")
                elif test_choice == "2":
                    send_command("test_stream")
                elif test_choice == "3":
                    break
                else:
                    print_colored(COLOR_RED, "❌ Geçersiz seçim!")
        else:
            print_colored(COLOR_RED, "Geçersiz seçim!")

if __name__ == "__main__":
    main()
