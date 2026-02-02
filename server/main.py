import logging
import os
import subprocess
from typing import List, Optional

import psutil
import pyautogui
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, Field, validator
from starlette import status

import mss
import mss.tools


API_TOKEN_ENV = "PC_REMOTE_API_TOKEN"
DEFAULT_API_TOKEN = "change-me"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("pc_remote")

app = FastAPI(title="PC Remote Server", version="1.0.0")


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


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
