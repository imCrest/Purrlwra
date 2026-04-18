package cock.crest.purrfectsnap.lite.ui.manager.theme.aphelion

/**
 * Finalized "Perfect Optic" AGSL Shader.
 * Features balanced thickness, high-impact refraction, and explosive kinetic energy.
 */
object WaveEdgeShader {

    const val AGSL = """
        uniform shader content;
        uniform float  revealRadius;
        uniform float2 revealCenter;
        uniform float  bandWidth;
        uniform float  time;
        uniform float  uProgress;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        float valueNoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
                u.y
            );
        }

        half4 main(float2 pos) {
            float d = distance(pos, revealCenter);
            
            float energy = (1.0 - uProgress);
            // Slower, more majestic large noise
            float noiseLarge = valueNoise(pos * 0.003 + time * 0.08) * 70.0;
            float totalNoise = noiseLarge * energy;
            
            float dist = d - (revealRadius + totalNoise);
            
            if (dist > 0.0) {
                return content.eval(pos);
            }

            // Reverting to balanced thickness (Starts at 50% width, grows to 100%)
            float dynamicBand = bandWidth * (0.5 + 0.5 * uProgress);
            float effectStart = revealRadius - dynamicBand;
            
            if (dist < -dynamicBand) {
                return half4(0.0);
            }

            float bandProgress = clamp((dist + dynamicBand) / dynamicBand, 0.0, 1.0);
            
            // Asymmetric crest: Sharp start, long slow tail
            float waveShape = pow(bandProgress, 2.0);
            
            float2 dir = normalize(pos - revealCenter + 0.001);
            
            // Refraction: Impactful but clear
            float refractionAmt = waveShape * (60.0 * energy + 25.0) + (totalNoise * 0.15);
            float2 refractedPos = pos + dir * refractionAmt;

            // Chromatic Aberration: Deep prism split
            float aberration = waveShape * (35.0 * energy + 10.0);
            half r = content.eval(refractedPos + dir * aberration).r;
            half g = content.eval(refractedPos).g;
            half b = content.eval(refractedPos - dir * aberration).b;

            // Sharp highlight at the front edge
            float highlight = pow(waveShape, 1.1) * (0.5 * energy + 0.15);
            
            // Leading edge softening (minor)
            float leadingEdgeFade = smoothstep(revealRadius, revealRadius - 10.0, d - totalNoise);
            float alpha = smoothstep(0.0, 0.25, bandProgress) * leadingEdgeFade;

            return half4(
                r + half(highlight),
                g + half(highlight),
                b + half(highlight),
                half(alpha)
            );
        }
    """
}
