from __future__ import annotations

import threading
import time

import uvicorn

from config import get_settings, validate_runtime_settings
from grpc_server import serve as serve_grpc
from main import app


def main() -> None:
    settings = get_settings()
    validate_runtime_settings(settings)

    # Run FastAPI in a background thread and keep gRPC on the main thread.
    # Playwright subprocess handling is more stable when gRPC/async loop lives in main thread.
    api_thread = threading.Thread(
        target=lambda: uvicorn.run(app, host=settings.http_host, port=settings.http_port, log_level="info"),
        name="fastapi-server",
        daemon=True,
    )
    api_thread.start()
    time.sleep(0.2)
    serve_grpc()


if __name__ == "__main__":
    main()
