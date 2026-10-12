"""Mô hình wire format — sao chép ĐÚNG những gì SDK Android gửi.

Nguồn sự thật là `src/main/java/com/app/plugin/logevent/EventRequest.kt`. Điểm dễ sai:
Gson chỉ đổi tên những trường có `@SerializedName`, nên `sequenceId`/`eventName`/
`eventData`/`userProperty` đi trên dây ở dạng **camelCase**, trong khi các trường của
`userProperty` lại là snake_case, và `timestamp` bị đổi thành `ts`. Trộn hai quy ước
trong một payload trông như lỗi nhưng đó là hình dạng thật — bịa lại cho "nhất quán"
sẽ khiến server im lặng bỏ qua dữ liệu.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class UserProperty(BaseModel):
    # Cho phép trường lạ: SDK có thể thêm thuộc tính mới trước khi backend kịp cập nhật,
    # và mất dữ liệu vì một trường chưa biết là kiểu hỏng tệ nhất ở lớp ingestion.
    model_config = ConfigDict(extra="allow")

    user_id: str = ""
    app_name: str = ""
    app_version: str = ""
    language: str = ""
    country: str = ""
    device_info: str = ""
    event_date: int = 0
    session_number: int = 0
    session_id: str = ""
    install_day: int = 0
    retention_day: int = 0
    local_hour: int = 0
    local_weekday: str = ""
    platform: str = ""
    ab_test: int = 0


class EventRequest(BaseModel):
    # `extra="allow"` còn là đường vào của envelope v2 (1.7.0): `event_id`, `schema_version`,
    # `sdk_version`, `package_name`, `consent_tier` nằm ở cấp cao nhất và được đọc qua
    # `model_extra` trong `normalize` — cố ý không khai kiểu ở đây: một trường envelope sai kiểu
    # thành NULL ở cột của nó, không làm cả event bị từ chối (400 = client bỏ bản ghi vĩnh viễn).
    model_config = ConfigDict(extra="allow")

    sequenceId: int = 0
    eventName: str
    eventData: dict[str, Any] = Field(default_factory=dict)
    # `ts` là mili-giây kể từ epoch, do CLIENT đặt. Phân vùng phải dựa vào nó chứ không
    # phải giờ nhận: hàng đợi bền vững của SDK replay bản ghi khi app mở lại, có thể
    # nhiều ngày sau (doc 46 mục C.3).
    ts: int = 0
    userProperty: UserProperty = Field(default_factory=UserProperty)
