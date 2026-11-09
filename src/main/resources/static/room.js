// Fixed 8×8 isometric room, with no furniture or external image dependencies.
export function roomSvg(editing = false) {
  const point = (x, y) => `${320 + (x - y) * 32},${230 + (x + y) * 16}`;
  let tiles = '';
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
    tiles += `<polygon points="${point(x,y)} ${point(x+1,y)} ${point(x+1,y+1)} ${point(x,y+1)}" fill="${(x+y)%2 ? '#f3d6b2' : '#ffeacd'}" stroke="${editing ? '#c98bb0' : '#deb58f'}" stroke-width=".8"/>`;
  }
  return `<svg class="room-svg" viewBox="0 0 640 540" role="img" aria-label="가구가 없는 8 곱하기 8 크기의 빈 집">
    <defs><filter id="shadow" x="-30%" y="-50%" width="160%" height="200%"><feGaussianBlur stdDeviation="13"/></filter><pattern id="wallpaper" width="14" height="14" patternUnits="userSpaceOnUse"><path d="M0 0V14" stroke="#ffe0ef" stroke-width="1"/></pattern></defs>
    <ellipse cx="324" cy="454" rx="227" ry="33" fill="#a35189" opacity=".12" filter="url(#shadow)"/>
    <path d="M64 358 320 486 576 358v15L320 501 64 373Z" fill="#c783a5"/>
    <path d="M64 358V184L320 56v174Z" fill="#f5aed0" stroke="#ce81ad"/>
    <path d="M64 358V184L320 56v174Z" fill="url(#wallpaper)"/>
    <path d="M320 56 576 184v174L320 230Z" fill="#ffd2e2" stroke="#dc99b9"/>
    <path d="M64 346 320 218 576 346v12L320 230 64 358Z" fill="#fff2cf"/>
    ${tiles}
    <path d="m406 150 90 45v88l-90-45Z" fill="#b88acb"/>
    <path d="m411 157 80 40v76l-80-40Z" fill="#ccecfb"/>
    <path d="m451 177 0 76m-40-58 80 40" fill="none" stroke="#fffaff" stroke-width="6"/>
    <path d="m401 234 100 50v8l-100-50Z" fill="#d8addd"/>
    <path d="m414 287 80 40-77 38-80-40Z" fill="#fffbed" opacity=".35"/>
    <path d="M64 184 320 56 576 184" fill="none" stroke="#fff9fd" stroke-width="6" stroke-linejoin="round"/>
  </svg>`;
}
