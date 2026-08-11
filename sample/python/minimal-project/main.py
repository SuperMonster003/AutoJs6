from helper import greeting
from autojs6 import app, device, execution, project


app_info = app.snapshot()
device_info = device.snapshot()
execution_info = execution.snapshot()
message = project.read_text("data/message.txt").strip()

print(
    f"{greeting()} | {message} | "
    f"{app_info['packageName']} | API {device_info['sdkInt']} | "
    f"{execution_info['entryPoint']}"
)
