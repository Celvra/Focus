use rand::Rng;
use std::collections::HashMap;
use std::net::IpAddr;
use std::time::{Duration, Instant};

const CODE_TTL: Duration = Duration::from_secs(300);
const FAIL_WINDOW: Duration = Duration::from_secs(60);
const FAIL_LIMIT: u32 = 3;

pub struct PairingState {
    code: String,
    issued_at: Instant,
    failures: HashMap<IpAddr, (Instant, u32)>,
}

impl PairingState {
    pub fn new() -> Self {
        Self {
            code: Self::generate(),
            issued_at: Instant::now(),
            failures: HashMap::new(),
        }
    }

    fn generate() -> String {
        let mut rng = rand::rng();
        format!("{:06}", rng.random_range(0..1_000_000))
    }

    pub fn code(&self) -> &str {
        &self.code
    }

    pub fn expired(&self) -> bool {
        self.issued_at.elapsed() > CODE_TTL
    }

    pub fn rotate(&mut self) {
        self.code = Self::generate();
        self.issued_at = Instant::now();
    }

    pub fn blocked(&mut self, ip: IpAddr) -> bool {
        match self.failures.get(&ip) {
            None => false,
            Some((since, count)) => {
                if since.elapsed() > FAIL_WINDOW {
                    self.failures.remove(&ip);
                    false
                } else {
                    *count >= FAIL_LIMIT
                }
            }
        }
    }

    pub fn record_failure(&mut self, ip: IpAddr) {
        let entry = self.failures.entry(ip).or_insert((Instant::now(), 0));
        if entry.0.elapsed() > FAIL_WINDOW {
            *entry = (Instant::now(), 1);
        } else {
            entry.1 += 1;
        }
    }

    pub fn clear_failures(&mut self, ip: IpAddr) {
        self.failures.remove(&ip);
    }

    pub fn verify(&self, submitted: &str) -> bool {
        if self.expired() {
            return false;
        }
        constant_time_eq(self.code.as_bytes(), submitted.as_bytes())
    }
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

