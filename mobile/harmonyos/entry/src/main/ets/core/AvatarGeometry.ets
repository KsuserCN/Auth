import { AvatarCropRegion, UserFacingError } from './Models';

export function clampCrop(width: number, height: number, region: AvatarCropRegion): AvatarCropRegion {
  if (![width, height, region.x, region.y, region.size].every((value: number) => Number.isFinite(value)) ||
    width < 1 || height < 1 || region.size < 1) { throw new UserFacingError('头像裁剪区域无效'); }
  const size = Math.max(1, Math.min(Math.floor(region.size), width, height));
  return { x: Math.floor(Math.max(0, Math.min(region.x, width - size))),
    y: Math.floor(Math.max(0, Math.min(region.y, height - size))), size: size };
}

export function avatarCropRegion(width: number, height: number, viewport: number,
  zoom: number, offsetX: number, offsetY: number): AvatarCropRegion {
  if (!Number.isFinite(viewport) || viewport <= 0 || !Number.isFinite(zoom)) { throw new UserFacingError('头像预览尚未就绪'); }
  const scale = Math.max(viewport / width, viewport / height) * Math.max(1, Math.min(6, zoom));
  const size = viewport / scale;
  return clampCrop(width, height, { x: (width - size) / 2 - offsetX / scale,
    y: (height - size) / 2 - offsetY / scale, size: size });
}
