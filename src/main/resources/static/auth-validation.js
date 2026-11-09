export function validateAuth(input, register) {
  const errors = {};
  if (!input.loginId.trim()) errors.loginId = '아이디를 입력해 주세요.';
  else if (register && !/^[a-zA-Z0-9_가-힣]{3,20}$/.test(input.loginId)) errors.loginId = '아이디는 한글·영문·숫자·밑줄 3~20자로 입력해 주세요.';
  if (!input.password.trim()) errors.password = '비밀번호를 입력해 주세요.';
  else if (register && input.password.length < 8) errors.password = '비밀번호는 8자 이상 입력해 주세요.';
  else if (new TextEncoder().encode(input.password).length > 72 || input.password.includes('\0')) errors.password = '비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.';
  if (register) {
    if (!input.email.trim()) errors.email = '이메일을 입력해 주세요.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email) || input.email.length > 254) errors.email = '올바른 이메일 주소를 입력해 주세요.';
  }
  return errors;
}
