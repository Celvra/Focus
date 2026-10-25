use clap::{Parser, Subcommand};

#[derive(Debug, Parser)]
#[command(name = "focus-bridge", version, about = "Remote shell and workspace bridge for Focus")]
pub struct Cli {
    #[command(subcommand)]
    pub command: Option<CommandKind>,
}

#[derive(Debug, Subcommand)]
pub enum CommandKind {
    Serve(ServeArgs),
    Pair,
    Devices,
    Revoke { id: String },
    Code,
}

#[derive(Debug, clap::Args)]
pub struct ServeArgs {
    #[arg(long)]
    pub port: Option<u16>,
    #[arg(long)]
    pub host: Option<String>,
}
