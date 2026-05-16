export const EASE_OUT_EXPO = [0.16, 1, 0.3, 1] as const;

export const fadeUp = {
  hidden: { opacity: 0, y: 8 },
  show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: EASE_OUT_EXPO } }
};

export const fadeUpLarge = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: EASE_OUT_EXPO } }
};

export const staggerContainer = (stagger = 0.08) => ({
  hidden: {},
  show: { transition: { staggerChildren: stagger } }
});

export const scaleIn = {
  hidden: { opacity: 0, scale: 0.95 },
  show: { opacity: 1, scale: 1, transition: { duration: 0.25, ease: EASE_OUT_EXPO } }
};

export const slideInFromRight = {
  hidden: { opacity: 0, x: 20 },
  show: { opacity: 1, x: 0, transition: { duration: 0.3, ease: EASE_OUT_EXPO } }
};

export const pageTransition = {
  initial: { opacity: 0, x: 20 },
  animate: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: -20 },
  transition: { duration: 0.3, ease: EASE_OUT_EXPO }
};
