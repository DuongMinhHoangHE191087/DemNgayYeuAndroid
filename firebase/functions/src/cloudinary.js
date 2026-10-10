'use strict';

// Lớp mỏng quanh SDK Cloudinary. Mọi lệnh gọi mạng và khoá bí mật đi qua đây; test truyền sdk giả.
// App không bao giờ nhận API secret hay token key. App chỉ nhận chữ ký upload gắn với đúng tham số đã ký
// và URL xem có token hết hạn. Không dùng preset upload không ký hay URL công khai.
const { resourceTypeOf } = require('./policy');

const HEX_KEY_RE = /^([0-9a-fA-F]{2}){16,}$/;

function httpCodeOf(err) {
  return err?.error?.http_code ?? err?.http_code;
}

function createCloudinary({ sdk, cloudName, apiKey, apiSecret, tokenKey }) {
  if (!cloudName || !apiKey || !apiSecret) {
    throw new Error('Thiếu cấu hình Cloudinary: cần cloud name, API key và API secret');
  }
  // Token key sai định dạng thì SDK vẫn tạo token nhưng không kiểm chứng được: chặn ngay thay vì lỗi im lặng.
  if (!HEX_KEY_RE.test(tokenKey ?? '')) {
    throw new Error('CLOUDINARY_TOKEN_KEY phải là chuỗi hex, tối thiểu 32 ký tự');
  }

  const v2 = sdk.v2;
  v2.config({ cloud_name: cloudName, api_key: apiKey, api_secret: apiSecret, secure: true });

  // Ký tham số upload. Chỉ ký những gì server quyết định: định dạng duy nhất, không ghi đè, loại riêng tư.
  // Tham số không ký (file, api_key) do app gửi; Cloudinary từ chối nếu tham số ký bị sửa.
  function signUpload({ publicId, kind, format, timestampSeconds }) {
    const params = {
      allowed_formats: format,
      overwrite: 'false',
      public_id: publicId,
      timestamp: String(timestampSeconds),
      type: 'authenticated',
    };
    const signature = v2.utils.api_sign_request(params, apiSecret);
    return {
      uploadUrl: `https://api.cloudinary.com/v1_1/${cloudName}/${resourceTypeOf(kind)}/upload`,
      fields: { ...params, api_key: apiKey, signature },
    };
  }

  // Trả về null khi tài sản không tồn tại (404); lỗi khác thì ném ra cho caller quyết định retry.
  async function fetchResource(publicId, kind, type = 'authenticated') {
    try {
      return await v2.api.resource(publicId, { resource_type: resourceTypeOf(kind), type });
    } catch (err) {
      if (httpCodeOf(err) === 404) return null;
      throw err;
    }
  }

  // Xoá tài sản và xoá cache CDN. "not found" coi là đã xoá (idempotent, an toàn khi retry).
  async function destroy(publicId, kind, type = 'authenticated') {
    const res = await v2.uploader.destroy(publicId, {
      resource_type: resourceTypeOf(kind),
      type,
      invalidate: true,
    });
    if (res.result !== 'ok' && res.result !== 'not found') {
      throw new Error(`Cloudinary destroy trả về "${res.result}"`);
    }
  }

  // Đổi tài sản public cũ thành authenticated và đổi tên theo quy ước mới. Link public cũ hết hiệu lực.
  function moveToPrivate(fromPublicId, toPublicId, kind) {
    return v2.uploader.rename(fromPublicId, toPublicId, {
      resource_type: resourceTypeOf(kind),
      type: 'upload',
      to_type: 'authenticated',
      overwrite: false,
      invalidate: true,
    });
  }

  // URL xem gắn với đúng đường dẫn tài sản và hết hạn sau ttlSeconds (HMAC-SHA256 bằng token key).
  // sign_url: true là bắt buộc: SDK chỉ sinh token khi bật sign_url, và khi có token thì SDK không thêm chữ ký s--.
  function deliveryUrl(publicId, kind, ttlSeconds) {
    return v2.url(publicId, {
      resource_type: resourceTypeOf(kind),
      type: 'authenticated',
      secure: true,
      sign_url: true,
      auth_token: { key: tokenKey, duration: ttlSeconds },
    });
  }

  return { signUpload, fetchResource, destroy, moveToPrivate, deliveryUrl };
}

module.exports = { createCloudinary, httpCodeOf };
