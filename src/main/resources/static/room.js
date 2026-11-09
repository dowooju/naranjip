// Fixed 8×8 isometric room, with no furniture or external image dependencies.
export function roomSvg(editing = false) {
  const point = (x, y) => `${320 + (x - y) * 32},${230 + (x + y) * 16}`;
  let tiles = '';
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
    tiles += `<polygon points="${point(x,y)} ${point(x+1,y)} ${point(x+1,y+1)} ${point(x,y+1)}" fill="${(x+y)%2 ? '#e6d7bd' : '#ebdec6'}" stroke="${editing ? '#bcae93' : '#d9cbb2'}" stroke-width=".8"/>`;
  }
  return `<svg class="room-svg" viewBox="0 0 640 540" role="img" aria-label="가구가 없는 8 곱하기 8 크기의 빈 집">
    <defs><filter id="shadow" x="-30%" y="-50%" width="160%" height="200%"><feGaussianBlur stdDeviation="13"/></filter><pattern id="wallpaper" width="14" height="14" patternUnits="userSpaceOnUse"><path d="M0 0V14" stroke="#dce1d2" stroke-width="1"/></pattern></defs>
    <ellipse cx="324" cy="454" rx="227" ry="33" fill="#67735e" opacity=".12" filter="url(#shadow)"/>
    <path d="M64 358 320 486 576 358v15L320 501 64 373Z" fill="#c5b496"/>
    <path d="M64 358V184L320 56v174Z" fill="#e4e8dc" stroke="#d0d6c8"/>
    <path d="M64 358V184L320 56v174Z" fill="url(#wallpaper)"/>
    <path d="M320 56 576 184v174L320 230Z" fill="#f6f3e9" stroke="#e4e0d3"/>
    <path d="M64 346 320 218 576 346v12L320 230 64 358Z" fill="#c9cfbc"/>
    ${tiles}
    <path d="m406 150 90 45v88l-90-45Z" fill="#d1c6ad"/>
    <path d="m411 157 80 40v76l-80-40Z" fill="#dce9e7"/>
    <path d="m451 177 0 76m-40-58 80 40" fill="none" stroke="#faf8ed" stroke-width="6"/>
    <path d="m401 234 100 50v8l-100-50Z" fill="#d4c8b1"/>
    <path d="m414 287 80 40-77 38-80-40Z" fill="#fffbed" opacity=".35"/>
    <path d="M64 184 320 56 576 184" fill="none" stroke="#fafaf4" stroke-width="6" stroke-linejoin="round"/>
  </svg>`;
}
