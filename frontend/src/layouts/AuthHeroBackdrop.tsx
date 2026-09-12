/**
 * CR-081. What sits behind the sign-in hero: a hardware-shop interior,
 * softly blurred under a dark overlay so the headline stays the subject.
 *
 * Two sources, chosen at build time:
 *
 * 1. A real photograph, if one exists at `src/assets/auth-hero.{jpg,png,webp}`.
 *    `import.meta.glob` with `eager` resolves to an empty object when the file
 *    is absent, so the build never fails on a missing asset and dropping a
 *    photo in is the whole change - no import to add, no code to touch.
 * 2. Otherwise the drawn interior below: two shelving bays in perspective,
 *    stocked with what a hardware shop stocks, a pegboard of tools, warm lamps.
 *    Under the blur it reads as atmosphere rather than illustration.
 *
 * The blur is modest (6px) on purpose - the owner's verdict on a heavier one
 * was that it just looked blurry. The overlay does the work of keeping text
 * legible; the blur only needs to stop the background competing for focus.
 *
 * Every colour in the drawn scene is literal, and that is correct here: it
 * stands in for a photograph, and a photograph does not follow the theme
 * either. The overlay on top IS token-driven (`--sidebar`), so the panel's
 * overall hue still shifts with the shop's theme.
 */
const photo = Object.values(
  import.meta.glob<{ default: string }>('/src/assets/auth-hero.{jpg,jpeg,png,webp}', { eager: true }),
)[0]?.default;

const BLUR = 'blur(6px)';

export function AuthHeroBackdrop() {
  if (photo) {
    return (
      <div
        aria-hidden
        className="pointer-events-none absolute -inset-4 bg-cover bg-center opacity-90"
        style={{ backgroundImage: `url(${photo})`, filter: BLUR }}
      />
    );
  }

  return (
    <svg
      aria-hidden
      className="pointer-events-none absolute -inset-4 h-[calc(100%+2rem)] w-[calc(100%+2rem)] opacity-90"
      style={{ filter: BLUR }}
      viewBox="0 30 840 760"
      preserveAspectRatio="xMidYMid slice"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id="ah-wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#1c3a2f" /><stop offset="1" stopColor="#10221b" /></linearGradient>
        <linearGradient id="ah-floor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#2a2f2a" /><stop offset="1" stopColor="#0d1512" /></linearGradient>
        <linearGradient id="ah-wood" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7a5a38" /><stop offset="1" stopColor="#4d3520" /></linearGradient>
        <radialGradient id="ah-lamp" cx="50%" cy="50%" r="50%"><stop offset="0" stopColor="#ffe9b3" stopOpacity=".9" /><stop offset="1" stopColor="#ffe9b3" stopOpacity="0" /></radialGradient>
        <pattern id="ah-peg" width="18" height="18" patternUnits="userSpaceOnUse"><circle cx="9" cy="9" r="1.6" fill="#0b1712" /></pattern>
      </defs>

      <rect x="0" y="0" width="840" height="720" fill="url(#ah-wall)" />
      <rect x="0" y="720" width="840" height="300" fill="url(#ah-floor)" />

      <ellipse cx="200" cy="60" rx="160" ry="90" fill="url(#ah-lamp)" />
      <ellipse cx="620" cy="70" rx="170" ry="95" fill="url(#ah-lamp)" />
      <rect x="150" y="34" width="100" height="10" rx="5" fill="#e9dcb6" />
      <rect x="570" y="40" width="100" height="10" rx="5" fill="#e9dcb6" />

      {/* pegboard of tools, centre back */}
      <rect x="290" y="110" width="260" height="330" fill="#3a5f4b" />
      <rect x="290" y="110" width="260" height="330" fill="url(#ah-peg)" />
      <rect x="318" y="150" width="8" height="110" rx="3" fill="#8b6b45" />
      <rect x="302" y="140" width="40" height="18" rx="3" fill="#3b3b3b" />
      <path d="M372 150 l10 0 l6 40 l-6 60 l-10 0 l6 -60 z" fill="#d23b2f" />
      <path d="M392 150 l10 0 l-6 40 l6 60 l-10 0 l-6 -60 z" fill="#d23b2f" />
      <rect x="425" y="145" width="9" height="46" rx="4" fill="#e0a12a" /><rect x="428" y="190" width="3" height="60" fill="#b8bcc0" />
      <rect x="445" y="145" width="9" height="46" rx="4" fill="#2f6fd6" /><rect x="448" y="190" width="3" height="60" fill="#b8bcc0" />
      <rect x="465" y="145" width="9" height="46" rx="4" fill="#d23b2f" /><rect x="468" y="190" width="3" height="60" fill="#b8bcc0" />
      <circle cx="520" cy="180" r="22" fill="#e5c94d" /><circle cx="520" cy="180" r="8" fill="#2a2a2a" />
      <rect x="310" y="300" width="220" height="16" rx="4" fill="#e8b12a" /><rect x="405" y="298" width="30" height="20" rx="3" fill="#c9d6dd" />
      <rect x="330" y="350" width="90" height="38" rx="10" fill="#1e9a4f" />
      <rect x="360" y="380" width="26" height="50" rx="7" fill="#1f1f1f" />
      <rect x="418" y="360" width="34" height="14" rx="4" fill="#8f9aa3" />
      <rect x="440" y="400" width="100" height="18" rx="4" fill="#e0a12a" />

      {/* left bay */}
      <polygon points="30,120 250,150 250,700 30,760" fill="#2b2118" />
      <polygon points="30,240 250,255 250,268 30,256" fill="url(#ah-wood)" />
      <polygon points="30,370 250,380 250,393 30,386" fill="url(#ah-wood)" />
      <polygon points="30,500 250,505 250,518 30,516" fill="url(#ah-wood)" />
      <polygon points="30,630 250,630 250,643 30,646" fill="url(#ah-wood)" />
      <rect x="40" y="170" width="60" height="70" fill="#c48a4a" /><rect x="48" y="190" width="44" height="14" fill="#f1e6cf" />
      <rect x="108" y="180" width="50" height="60" fill="#e0a12a" /><rect x="114" y="200" width="38" height="12" fill="#f8f2e0" />
      <rect x="166" y="175" width="72" height="66" fill="#2f6fd6" /><rect x="176" y="196" width="52" height="14" fill="#eef3fb" />
      <rect x="44" y="300" width="42" height="70" rx="4" fill="#d9d9d9" /><rect x="44" y="320" width="42" height="26" fill="#d23b2f" />
      <rect x="94" y="300" width="42" height="70" rx="4" fill="#d9d9d9" /><rect x="94" y="320" width="42" height="26" fill="#2f6fd6" />
      <rect x="144" y="300" width="42" height="70" rx="4" fill="#d9d9d9" /><rect x="144" y="320" width="42" height="26" fill="#1e9a4f" />
      <rect x="194" y="300" width="42" height="70" rx="4" fill="#d9d9d9" /><rect x="194" y="320" width="42" height="26" fill="#e0a12a" />
      <rect x="42" y="440" width="46" height="60" fill="#8a6a45" />
      <rect x="96" y="450" width="60" height="50" fill="#c9a24a" />
      <rect x="164" y="436" width="74" height="64" fill="#a37f55" />
      <rect x="40" y="565" width="90" height="62" rx="12" fill="#b9b3a4" />
      <rect x="140" y="565" width="90" height="62" rx="12" fill="#a8a294" />

      {/* right bay */}
      <polygon points="590,150 810,120 810,760 590,700" fill="#2b2118" />
      <polygon points="590,255 810,240 810,256 590,268" fill="url(#ah-wood)" />
      <polygon points="590,380 810,370 810,386 590,393" fill="url(#ah-wood)" />
      <polygon points="590,505 810,500 810,516 590,518" fill="url(#ah-wood)" />
      <polygon points="590,630 810,630 810,646 590,643" fill="url(#ah-wood)" />
      <rect x="600" y="196" width="200" height="12" rx="6" fill="#cfd5d8" />
      <rect x="600" y="212" width="200" height="12" rx="6" fill="#b5bcc0" />
      <rect x="600" y="228" width="200" height="12" rx="6" fill="#cfd5d8" />
      <circle cx="640" cy="335" r="30" fill="#2f6fd6" /><circle cx="640" cy="335" r="10" fill="#2b2118" />
      <circle cx="712" cy="335" r="30" fill="#d23b2f" /><circle cx="712" cy="335" r="10" fill="#2b2118" />
      <circle cx="784" cy="335" r="26" fill="#e0a12a" /><circle cx="784" cy="335" r="9" fill="#2b2118" />
      <rect x="598" y="440" width="70" height="60" fill="#c48a4a" /><rect x="606" y="458" width="54" height="14" fill="#f1e6cf" />
      <rect x="676" y="446" width="56" height="54" fill="#1e9a4f" />
      <rect x="740" y="436" width="64" height="64" fill="#8a6a45" />
      <path d="M600 570 h60 l-6 58 h-48 z" fill="#2f6fd6" />
      <path d="M672 570 h60 l-6 58 h-48 z" fill="#1e9a4f" />
      <path d="M744 570 h60 l-6 58 h-48 z" fill="#d9d9d9" />

      {/* counter and lamp pools */}
      <rect x="0" y="760" width="840" height="70" fill="#5a3f27" />
      <rect x="0" y="750" width="840" height="14" fill="#8a6a45" />
      <ellipse cx="200" cy="900" rx="170" ry="40" fill="#ffe9b3" opacity=".08" />
      <ellipse cx="620" cy="910" rx="170" ry="40" fill="#ffe9b3" opacity=".08" />
    </svg>
  );
}
