import json
import logging
import os
import socket
import subprocess
import threading
import time
import uuid
from datetime import datetime, timezone
from typing import Dict, List, Optional

import psutil
import pyautogui
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse
from pydantic import BaseModel, Field, validator
from starlette import status

import mss
import mss.tools


API_TOKEN_ENV = "PC_REMOTE_API_TOKEN"
DEFAULT_API_TOKEN = "change-me"
DISCOVERY_PORT = 9999
DISCOVERY_MESSAGE = "PC_REMOTE_DISCOVERY"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("pc_remote")

app = FastAPI(title="PC Remote Server", version="1.0.0")
SESSION_SWEEP_INTERVAL = 30
DEFAULT_SESSION_TIMEOUT = 15 * 60
STREAM_BOUNDARY = "frame"
RECORDINGS_DIR = os.path.join(os.path.dirname(__file__), "recordings")

active_sessions: Dict[str, Dict[str, float | str]] = {}
sessions_lock = threading.Lock()
recordings_lock = threading.Lock()
active_recordings: Dict[str, Dict[str, object]] = {}


def start_discovery_listener():
    def listen():
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.bind(("0.0.0.0", DISCOVERY_PORT))
            logger.info("Discovery listener started on UDP %s", DISCOVERY_PORT)
            while True:
                data, addr = sock.recvfrom(1024)
                message = data.decode("utf-8", errors="ignore").strip()
                if message != DISCOVERY_MESSAGE:
                    continue
                payload = json.dumps(
                    {"port": 8000, "token": get_configured_token()},
                ).encode("utf-8")
                sock.sendto(payload, addr)

    thread = threading.Thread(target=listen, name="discovery-listener", daemon=True)
    thread.start()

def start_session_sweeper():
    def sweep():
        while True:
            now = time.time()
            with sessions_lock:
                expired = [
                    session_id
                    for session_id, info in active_sessions.items()
                    if now - info["last_seen"] > info["timeout"]
                ]
                for session_id in expired:
                    del active_sessions[session_id]
            time.sleep(SESSION_SWEEP_INTERVAL)

    thread = threading.Thread(target=sweep, name="session-sweeper", daemon=True)
    thread.start()


@app.on_event("startup")
async def startup_event():
    start_discovery_listener()
    start_session_sweeper()


def get_configured_token() -> str:
    return os.getenv(API_TOKEN_ENV, DEFAULT_API_TOKEN)


def extract_token(request: Request) -> Optional[str]:
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.lower().startswith("bearer "):
        return auth_header.split(" ", 1)[1].strip()
    return request.headers.get("X-API-Token")


@app.middleware("http")
async def token_middleware(request: Request, call_next):
    if request.url.path == "/auth":
        return await call_next(request)

    token = extract_token(request)
    expected = get_configured_token()
    if not token or token != expected:
        logger.warning("Unauthorized request to %s", request.url.path)
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"detail": "Invalid or missing API token."},
        )

    response = await call_next(request)
    return response


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    logger.error("HTTP error on %s: %s", request.url.path, exc.detail)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled error on %s", request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error."},
    )


class AuthRequest(BaseModel):
    token: str


class MouseMoveRequest(BaseModel):
    x: int
    y: int
    duration: float = 0.0
    absolute: bool = True


class MouseClickRequest(BaseModel):
    button: str = Field("left", pattern="^(left|right|middle)$")
    clicks: int = Field(1, ge=1, le=3)
    interval: float = Field(0.0, ge=0.0, le=5.0)
    x: Optional[int] = None
    y: Optional[int] = None


class KeyboardPressRequest(BaseModel):
    key: Optional[str] = None
    keys: Optional[List[str]] = None
    presses: int = Field(1, ge=1, le=10)
    interval: float = Field(0.0, ge=0.0, le=2.0)

    @validator("keys", always=True)
    def validate_keys(cls, value, values):
        if not value and not values.get("key"):
            raise ValueError("Provide either 'key' or 'keys'.")
        return value


class SystemVolumeRequest(BaseModel):
    action: str = Field(..., pattern="^(up|down|mute)$")
    steps: int = Field(1, ge=1, le=20)


class SystemLaunchRequest(BaseModel):
    command: str
    args: Optional[List[str]] = None


class SessionStartRequest(BaseModel):
    client_name: Optional[str] = None
    timeout_seconds: int = Field(DEFAULT_SESSION_TIMEOUT, ge=30, le=24 * 60 * 60)


class SessionHeartbeatRequest(BaseModel):
    session_id: str


class SessionEndRequest(BaseModel):
    session_id: str


class SystemPowerRequest(BaseModel):
    action: str = Field(
        ...,
        pattern="^(shutdown|restart|lock|logoff|sleep|hibernate)$",
    )


class CameraStreamRequest(BaseModel):
    fps: int = Field(5, ge=1, le=30)
    quality: int = Field(80, ge=30, le=95)
    device_index: int = Field(0, ge=0, le=10)


class ScreenRecordStartRequest(BaseModel):
    fps: int = Field(10, ge=1, le=30)
    duration_seconds: Optional[int] = Field(None, ge=1, le=24 * 60 * 60)


@app.post("/auth")
async def auth(request: AuthRequest):
    is_valid = request.token == get_configured_token()
    logger.info("Auth attempt: %s", "success" if is_valid else "failure")
    if not is_valid:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    return {"status": "ok"}


@app.post("/mouse/move")
async def mouse_move(request: MouseMoveRequest):
    logger.info("Mouse move: %s", request.json())
    if request.absolute:
        pyautogui.moveTo(request.x, request.y, duration=request.duration)
    else:
        pyautogui.moveRel(request.x, request.y, duration=request.duration)
    return {"status": "ok"}


@app.post("/mouse/click")
async def mouse_click(request: MouseClickRequest):
    logger.info("Mouse click: %s", request.json())
    pyautogui.click(
        x=request.x,
        y=request.y,
        button=request.button,
        clicks=request.clicks,
        interval=request.interval,
    )
    return {"status": "ok"}


@app.post("/keyboard/press")
async def keyboard_press(request: KeyboardPressRequest):
    logger.info("Keyboard press: %s", request.json())
    if request.keys:
        pyautogui.hotkey(*request.keys)
    else:
        pyautogui.press(request.key, presses=request.presses, interval=request.interval)
    return {"status": "ok"}


@app.post("/system/volume")
async def system_volume(request: SystemVolumeRequest):
    logger.info("Volume control: %s", request.json())
    key_map = {"up": "volumeup", "down": "volumedown", "mute": "volumemute"}
    key = key_map[request.action]
    for _ in range(request.steps):
        pyautogui.press(key)
    return {"status": "ok"}


@app.post("/system/launch")
async def system_launch(request: SystemLaunchRequest):
    logger.info("Launch command: %s", request.json())
    args = [request.command] + (request.args or [])
    try:
        subprocess.Popen(args, shell=False)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc))
    return {"status": "ok"}


@app.get("/system/status")
async def system_status():
    logger.info("Status request")
    cpu_percent = psutil.cpu_percent(interval=0.2)
    memory = psutil.virtual_memory()
    return {
        "cpu_percent": cpu_percent,
        "memory": {
            "total": memory.total,
            "available": memory.available,
            "used": memory.used,
            "percent": memory.percent,
        },
    }


@app.get("/screen/screenshot")
async def screen_screenshot():
    logger.info("Screenshot request")
    with mss.mss() as sct:
        monitor = sct.monitors[1]
        shot = sct.grab(monitor)
        png_bytes = mss.tools.to_png(shot.rgb, shot.size)
    return Response(content=png_bytes, media_type="image/png")


def create_session(request: SessionStartRequest) -> Dict[str, str]:
    session_id = str(uuid.uuid4())
    now = time.time()
    with sessions_lock:
        active_sessions[session_id] = {
            "client_name": request.client_name or "unknown",
            "created_at": now,
            "last_seen": now,
            "timeout": float(request.timeout_seconds),
        }
    return {
        "session_id": session_id,
        "expires_at": datetime.fromtimestamp(now + request.timeout_seconds, tz=timezone.utc).isoformat(),
    }


def touch_session(session_id: str) -> Dict[str, str]:
    now = time.time()
    with sessions_lock:
        info = active_sessions.get(session_id)
        if not info:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found.")
        if now - info["last_seen"] > info["timeout"]:
            del active_sessions[session_id]
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="Session expired.")
        info["last_seen"] = now
        expires_at = datetime.fromtimestamp(now + info["timeout"], tz=timezone.utc).isoformat()
    return {"session_id": session_id, "expires_at": expires_at}


def end_session(session_id: str) -> None:
    with sessions_lock:
        if session_id not in active_sessions:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found.")
        del active_sessions[session_id]


def ensure_windows_action() -> None:
    if os.name != "nt":
        raise HTTPException(status_code=status.HTTP_501_NOT_IMPLEMENTED, detail="Power actions supported on Windows only.")


def run_power_action(action: str) -> None:
    ensure_windows_action()
    commands = {
        "shutdown": ["shutdown", "/s", "/t", "0"],
        "restart": ["shutdown", "/r", "/t", "0"],
        "lock": ["rundll32.exe", "user32.dll,LockWorkStation"],
        "logoff": ["shutdown", "/l"],
        "sleep": ["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"],
        "hibernate": ["shutdown", "/h"],
    }
    command = commands[action]
    logger.info("Power action: %s", action)
    subprocess.Popen(command, shell=False)


def encode_png_bytes(shot: mss.base.ScreenShot) -> bytes:
    return mss.tools.to_png(shot.rgb, shot.size)


def multipart_frame(png_bytes: bytes) -> bytes:
    return (
        f"--{STREAM_BOUNDARY}\r\n"
        "Content-Type: image/png\r\n\r\n"
    ).encode("utf-8") + png_bytes + b"\r\n"


def generate_screen_stream(fps: int):
    interval = 1.0 / fps
    with mss.mss() as sct:
        monitor = sct.monitors[1]
        while True:
            start = time.time()
            shot = sct.grab(monitor)
            png_bytes = encode_png_bytes(shot)
            yield multipart_frame(png_bytes)
            elapsed = time.time() - start
            if elapsed < interval:
                time.sleep(interval - elapsed)


def generate_camera_stream(fps: int, quality: int, device_index: int):
    try:
        import cv2
    except ImportError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Camera streaming requires opencv-python.",
        ) from exc

    cap = cv2.VideoCapture(device_index)
    if not cap.isOpened():
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Camera not available.")
    interval = 1.0 / fps
    try:
        while True:
            start = time.time()
            ok, frame = cap.read()
            if not ok:
                raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Camera frame failed.")
            success, buffer = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), quality])
            if not success:
                raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Failed to encode image.")
            yield (
                f"--{STREAM_BOUNDARY}\r\n"
                "Content-Type: image/jpeg\r\n\r\n"
            ).encode("utf-8") + buffer.tobytes() + b"\r\n"
            elapsed = time.time() - start
            if elapsed < interval:
                time.sleep(interval - elapsed)
    finally:
        cap.release()


def capture_camera_photo(device_index: int) -> bytes:
    try:
        import cv2
    except ImportError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Camera capture requires opencv-python.",
        ) from exc

    cap = cv2.VideoCapture(device_index)
    if not cap.isOpened():
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Camera not available.")
    try:
        ok, frame = cap.read()
        if not ok:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Camera frame failed.")
        success, buffer = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
        if not success:
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Failed to encode image.")
        return buffer.tobytes()
    finally:
        cap.release()


def ensure_recordings_dir() -> None:
    os.makedirs(RECORDINGS_DIR, exist_ok=True)


def record_screen_task(
    recording_id: str,
    fps: int,
    duration_seconds: Optional[int],
    stop_event: threading.Event,
):
    ensure_recordings_dir()
    with mss.mss() as sct:
        monitor = sct.monitors[1]
        width = monitor["width"]
        height = monitor["height"]
        filename = os.path.join(RECORDINGS_DIR, f"screen_{recording_id}.mpng")
        start_time = time.time()
        try:
            with open(filename, "ab") as handle:
                while not stop_event.is_set():
                    shot = sct.grab(monitor)
                    png_bytes = encode_png_bytes(shot)
                    frame_bytes = multipart_frame(png_bytes)
                    handle.write(frame_bytes)
                    if duration_seconds and time.time() - start_time >= duration_seconds:
                        break
                    time.sleep(max(0.0, (1.0 / fps)))
        finally:
            with recordings_lock:
                info = active_recordings.get(recording_id)
                if info:
                    info["completed"] = True


@app.post("/session/start")
async def session_start(request: SessionStartRequest):
    logger.info("Session start: %s", request.json())
    return create_session(request)


@app.post("/session/heartbeat")
async def session_heartbeat(request: SessionHeartbeatRequest):
    logger.info("Session heartbeat: %s", request.session_id)
    return touch_session(request.session_id)


@app.post("/session/end")
async def session_end(request: SessionEndRequest):
    logger.info("Session end: %s", request.session_id)
    end_session(request.session_id)
    return {"status": "ok"}


@app.get("/session/status/{session_id}")
async def session_status(session_id: str):
    logger.info("Session status: %s", session_id)
    return touch_session(session_id)


@app.post("/system/power")
async def system_power(request: SystemPowerRequest):
    run_power_action(request.action)
    return {"status": "ok", "action": request.action}


@app.get("/screen/stream")
async def screen_stream(fps: int = 5):
    logger.info("Screen stream request fps=%s", fps)
    return StreamingResponse(
        generate_screen_stream(fps),
        media_type=f"multipart/x-mixed-replace; boundary={STREAM_BOUNDARY}",
    )


@app.get("/camera/stream")
async def camera_stream(fps: int = 5, quality: int = 80, device_index: int = 0):
    logger.info("Camera stream request fps=%s quality=%s device=%s", fps, quality, device_index)
    return StreamingResponse(
        generate_camera_stream(fps, quality, device_index),
        media_type=f"multipart/x-mixed-replace; boundary={STREAM_BOUNDARY}",
    )


@app.get("/camera/photo")
async def camera_photo(device_index: int = 0):
    logger.info("Camera photo request device=%s", device_index)
    photo = capture_camera_photo(device_index)
    return Response(content=photo, media_type="image/jpeg")


@app.post("/screen/record/start")
async def screen_record_start(request: ScreenRecordStartRequest):
    logger.info("Screen record start: %s", request.json())
    ensure_recordings_dir()
    recording_id = str(uuid.uuid4())
    stop_event = threading.Event()
    thread = threading.Thread(
        target=record_screen_task,
        args=(recording_id, request.fps, request.duration_seconds, stop_event),
        daemon=True,
    )
    with recordings_lock:
        active_recordings[recording_id] = {
            "thread": thread,
            "stop_event": stop_event,
            "fps": request.fps,
            "started_at": datetime.now(tz=timezone.utc).isoformat(),
            "duration_seconds": request.duration_seconds,
            "completed": False,
            "file": os.path.join(RECORDINGS_DIR, f"screen_{recording_id}.mpng"),
        }
    thread.start()
    return {"status": "ok", "recording_id": recording_id}


@app.post("/screen/record/stop/{recording_id}")
async def screen_record_stop(recording_id: str):
    logger.info("Screen record stop: %s", recording_id)
    with recordings_lock:
        info = active_recordings.get(recording_id)
        if not info:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found.")
        info["stop_event"].set()
    return {"status": "ok", "recording_id": recording_id}


@app.get("/screen/recordings")
async def screen_recordings():
    with recordings_lock:
        recordings = {
            recording_id: {
                "started_at": info["started_at"],
                "fps": info["fps"],
                "duration_seconds": info["duration_seconds"],
                "completed": info["completed"],
                "file": info["file"],
            }
            for recording_id, info in active_recordings.items()
        }
    return {"recordings": recordings}


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
