import socket
import threading
import subprocess
import sys

PORT = 8888
DISCOVERY_PORT = 9999
DISCOVER_MESSAGE = "SMSSYNC_DISCOVER"
SERVER_RESPONSE_PREFIX = "SMSSYNC_SERVER"

def get_local_ips():
    ips = []
    try:
        host_name = socket.gethostname()
        for ip in socket.gethostbyname_ex(host_name)[2]:
            if not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip not in ips and not ip.startswith("127."):
            ips.insert(0, ip)
    except Exception:
        pass
        
    return ips

def is_mac():
    return sys.platform == 'darwin'

def set_clipboard(text):
    if is_mac():
        try:
            p = subprocess.Popen(['pbcopy'], stdin=subprocess.PIPE)
            p.communicate(input=text.encode('utf-8'))
        except Exception as e:
            print(f"剪贴板错误: {e}")
    else:
        if sys.platform == 'win32':
            try:
                p = subprocess.Popen(['clip'], stdin=subprocess.PIPE)
                p.communicate(input=text.encode('utf-16le'))
            except:
                pass
        else:
            try:
                p = subprocess.Popen(['xclip', '-selection', 'clipboard'], stdin=subprocess.PIPE)
                p.communicate(input=text.encode('utf-8'))
            except:
                pass
    print("成功复制到剪贴板！")

def handle_client(conn, addr):
    with conn:
        data = conn.recv(1024)
        if data:
            message = data.decode('utf-8').strip()
            if message:
                print(f"接收到消息: {message}")
                set_clipboard(message)

def tcp_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('', PORT))
        s.listen()
        print(f"服务端启动！正在监听端口 {PORT}")
        print("本机 IP 地址:")
        for ip in get_local_ips():
            print(f"  {ip}")
            
        while True:
            conn, addr = s.accept()
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()

def discovery_server():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('', DISCOVERY_PORT))
        print(f"自动发现服务启动！正在监听端口 {DISCOVERY_PORT}")
        
        while True:
            data, addr = s.recvfrom(1024)
            message = data.decode('utf-8').strip()
            if message == DISCOVER_MESSAGE:
                ips = get_local_ips()
                server_ip = ips[0] if ips else "127.0.0.1"
                
                client_prefix = ".".join(addr[0].split('.')[:3])
                for ip in ips:
                    if ip.startswith(client_prefix):
                        server_ip = ip
                        break
                        
                response = f"{SERVER_RESPONSE_PREFIX}|{server_ip}|{PORT}"
                s.sendto(response.encode('utf-8'), addr)
                print(f"发现请求来自: {addr[0]}")
                print(f"已回复自动发现请求: {server_ip}")

if __name__ == '__main__':
    threading.Thread(target=discovery_server, daemon=True).start()
    tcp_server()
