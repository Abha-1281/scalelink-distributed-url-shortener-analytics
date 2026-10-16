"""Initial schema for hourly_click_aggregates and click_events

Revision ID: 001_initial_schema
Revises: 
Create Date: 2026-10-12 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '001_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Create hourly_click_aggregates table
    op.create_table(
        'hourly_click_aggregates',
        sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column('short_code', sa.String(length=32), nullable=False),
        sa.Column('bucket_hour', sa.DateTime(timezone=True), nullable=False),
        sa.Column('country', sa.String(length=3), nullable=False, server_default='ZZ'),
        sa.Column('device', sa.String(length=32), nullable=False, server_default='Desktop'),
        sa.Column('browser', sa.String(length=64), nullable=False, server_default='Unknown'),
        sa.Column('click_count', sa.Integer(), nullable=False, server_default='1'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('short_code', 'bucket_hour', 'country', 'device', 'browser', name='uq_hourly_aggregate')
    )
    op.create_index('idx_hourly_short_code', 'hourly_click_aggregates', ['short_code'])
    op.create_index('idx_hourly_bucket_hour', 'hourly_click_aggregates', ['bucket_hour'])
    op.create_index('idx_aggregate_lookup', 'hourly_click_aggregates', ['short_code', 'bucket_hour'])

    # Create click_events audit table
    op.create_table(
        'click_events',
        sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column('event_id', sa.String(length=36), nullable=False),
        sa.Column('short_code', sa.String(length=32), nullable=False),
        sa.Column('original_url', sa.String(), nullable=True),
        sa.Column('timestamp', sa.DateTime(timezone=True), nullable=False),
        sa.Column('country', sa.String(length=3), nullable=False, server_default='ZZ'),
        sa.Column('device', sa.String(length=32), nullable=False, server_default='Desktop'),
        sa.Column('browser', sa.String(length=64), nullable=False, server_default='Unknown'),
        sa.Column('referer', sa.String(length=256), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('event_id', name='uq_click_event_id')
    )
    op.create_index('idx_click_events_short_code', 'click_events', ['short_code'])


def downgrade() -> None:
    op.drop_table('click_events')
    op.drop_table('hourly_click_aggregates')
